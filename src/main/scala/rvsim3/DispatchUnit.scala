package rvsim3

import chisel3._
import chisel3.util._
import Config._

class DUToFL extends Bundle {
  // empty: No additional information needed
}

class DUToRF extends Bundle {
  val rs1PhysIdx = PhysIndex
  val rs2PhysIdx = PhysIndex

  val allocEn = Bool() // whether allocated a new physReg (from FreeList)
  val allocPhysIdx = PhysIndex
}

class RegisterAliasTable extends Module {
  val io = IO(new Bundle {
    val rs1ArchIdx = Input(ArchIndex)
    val rs1PhysIdx = Output(PhysIndex)
    val rs2ArchIdx = Input(ArchIndex)
    val rs2PhysIdx = Output(PhysIndex)
    val rdArchIdx = Input(ArchIndex)
    val rdPhysIdx = Output(PhysIndex)
    
    val updateEn  = Input(Bool())
    val updateArch = Input(ArchIndex)
    val updatePhys = Input(PhysIndex)

    val rollbackEn   = Input(Bool())
    val rollbackArch = Input(ArchIndex)
    val rollbackPhys = Input(PhysIndex)

    // debug check
    val x10PhysIdx = Output(PhysIndex)
  })

  // initialize: R0 -> P0, R1 -> P1 ... R31 -> P31
  val table = RegInit(VecInit(Seq.tabulate(NumArchRegs)(i => i.U(PhysIndexLen.W))))

  io.rs1PhysIdx := table(io.rs1ArchIdx)
  io.rs2PhysIdx := table(io.rs2ArchIdx)
  io.rdPhysIdx  := table(io.rdArchIdx)

  io.x10PhysIdx := table(10)

  when(io.rollbackEn) {
    table(io.rollbackArch) := io.rollbackPhys
  }.elsewhen(io.updateEn && io.updateArch =/= 0.U) {
    table(io.updateArch) := io.updatePhys
  }
}

class DispatchUnit extends Module {
  val io = IO(new Bundle {
    val decIn  = Flipped(Decoupled(new DecToDU))
    val robOut = Decoupled(new RoBEntry)
    val robIn  = Flipped(Decoupled(new RoBToDU))
    val robFlush = Flipped(Decoupled(new RoBToDUFlush))
    val flOut  = Decoupled(new DUToFL)
    val flIn   = Flipped(Decoupled(new FLToDU))
    val lsqOut = Decoupled(new LSQEntry)
    val rsOut  = Decoupled(new RSEntry)
    val rfIn   = Input(new RFToDU)
    val rfOut  = Output(new DUToRF) // always ready.
    
    val x10PhysIdx = Output(PhysIndex)
  })
  
  val rat = Module(new RegisterAliasTable)
  io.x10PhysIdx := rat.io.x10PhysIdx

  object State extends ChiselEnum {
    val sIdle, sDispatch = Value
  }
  val state = RegInit(State.sIdle)

  val instr     = io.decIn.bits.decInstr
  val isMem     = instr.isLoad || instr.isStore
  val isWriteRf = instr.writeRf
  val needPhys  = isWriteRf && (instr.rd =/= 0.U)

  // RAT query: rs1, rs2, and rd (to get prePhysIdx)
  rat.io.rs1ArchIdx := instr.rs1
  rat.io.rs2ArchIdx := instr.rs2
  rat.io.rdArchIdx  := instr.rd

  // FreeList Head
  val newPhysIdx = io.flIn.bits.physIdx

  // RF: read current values and mark new register as "not ready"
  io.rfOut.rs1PhysIdx   := rat.io.rs1PhysIdx
  io.rfOut.rs2PhysIdx   := rat.io.rs2PhysIdx
  io.rfOut.allocEn      := false.B // default
  io.rfOut.allocPhysIdx := newPhysIdx

  // dispatch condition
  val backendReady = Mux(isMem, io.lsqOut.ready, io.rsOut.ready)
  val canDispatch  = io.decIn.valid && io.robIn.valid && io.robOut.ready && 
                     (!needPhys || io.flIn.valid) && backendReady

  // 2. default outputs
  io.decIn.ready  := false.B
  io.flOut.valid  := false.B
  io.robOut.valid := false.B
  io.rsOut.valid  := false.B
  io.lsqOut.valid := false.B
  
  rat.io.updateEn   := false.B
  rat.io.updateArch := instr.rd
  rat.io.updatePhys := newPhysIdx

  // RoB rollback logic
  rat.io.rollbackEn   := io.robFlush.valid
  rat.io.rollbackArch := io.robFlush.bits.archIdx
  rat.io.rollbackPhys := io.robFlush.bits.prePhysIdx
  io.robFlush.ready   := true.B 

  // 3. state machine
  switch(state) {
    is(State.sIdle) {
      when(io.decIn.valid && !io.robFlush.valid) {
        state := State.sDispatch
      }
    }
    
    is(State.sDispatch) {
      when(io.robFlush.valid) {
        state := State.sIdle
      }.elsewhen(canDispatch) {
        // handshake success
        io.decIn.ready   := true.B
        io.robOut.valid  := true.B
        io.flOut.valid   := needPhys
        rat.io.updateEn  := needPhys
        io.rfOut.allocEn := needPhys

        // setup RoB entry
        io.robOut.bits.decInstr   := instr
        io.robOut.bits.physIdx    := Mux(needPhys, newPhysIdx, 0.U)
        io.robOut.bits.prePhysIdx := rat.io.rdPhysIdx // Save old mapping for recovery/recycling
        io.robOut.bits.isReady    := false.B
        io.robOut.bits.value      := 0.U
        io.robOut.bits.pc         := io.decIn.bits.pc
        io.robOut.bits.predPC     := io.decIn.bits.predPC

        // pass to RS or LSQ
        when(isMem) {
          io.lsqOut.valid := true.B
          io.lsqOut.bits.isWrite      := instr.isStore
          io.lsqOut.bits.mask         := instr.memDataMask
          io.lsqOut.bits.addrReady    := false.B
          io.lsqOut.bits.addr         := 0.U
          // Address calculation uses current physIdx for CDB broadcast matching
          io.lsqOut.bits.addrPhysIdx  := Mux(needPhys, newPhysIdx, 0.U) 
          
          io.lsqOut.bits.dataReady    := Mux(instr.isStore, io.rfIn.rs2Ready, false.B)
          io.lsqOut.bits.data         := io.rfIn.rs2Value
          io.lsqOut.bits.dataPhysIdx  := Mux(instr.isStore, rat.io.rs2PhysIdx, rat.io.rdPhysIdx)
          
          io.lsqOut.bits.robIdx       := io.robIn.bits.robIdx
          io.lsqOut.bits.readyToIssue := !instr.isStore
        }.otherwise {
          io.rsOut.valid := true.B
          io.rsOut.bits.decInstr    := instr
          io.rsOut.bits.robIdx      := io.robIn.bits.robIdx
          io.rsOut.bits.physIdx     := newPhysIdx
          
          // src1
          io.rsOut.bits.rs1Ready    := !instr.hasSrc1 || io.rfIn.rs1Ready
          io.rsOut.bits.rs1PhysIdx  := rat.io.rs1PhysIdx
          io.rsOut.bits.rs1Value    := io.rfIn.rs1Value
          
          // src2 (register or immediate)
          io.rsOut.bits.rs2Ready    := !instr.hasSrc2 || io.rfIn.rs2Ready
          io.rsOut.bits.rs2PhysIdx  := rat.io.rs2PhysIdx
          io.rsOut.bits.rs2Value    := Mux(instr.hasSrc2, io.rfIn.rs2Value, instr.imm)
        }

        state := State.sIdle
      }
    }
  }
}