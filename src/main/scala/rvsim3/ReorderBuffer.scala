package rvsim3

import chisel3._
import chisel3.util._
import Config._

class RoBToLSQ extends Bundle {
  val robIdx = RoBIndex
}

class RoBToPred extends Bundle {
  val instrAddr   = Addr
  val actualPC    = Addr
  val actualTaken = Bool()
}

class RoBToDU extends Bundle {
  val robIdx = RoBIndex
}

class RoBToDUFlush extends Bundle {
  val archIdx    = ArchIndex
  val prePhysIdx = PhysIndex
}

class RoBToFL extends Bundle {
  val physIdx = PhysIndex
}

class RoBEntry extends Bundle {
  val pc          = Addr
  val predPC      = Addr
  val decInstr    = new DecodedInst
  val physIdx     = PhysIndex
  val prePhysIdx  = PhysIndex
  val isReady     = Bool()
  val value       = XData
  val addr        = Addr
}

class ReorderBuffer extends Module {
  val io = IO(new Bundle {
    val duIn    = Flipped(Decoupled(new RoBEntry))
    val duOut   = Decoupled(new RoBToDU)
    val duFlush = Decoupled(new RoBToDUFlush)
    val lsqOut  = Valid(new RoBToLSQ)
    val predOut = Valid(new RoBToPred)
    val flOut   = Decoupled(new RoBToFL)
    val cdbIn   = Flipped(Valid(new CDBPayload))
    val flushOut = Valid(new FlushPipeline)

    val isTerminated = Bool()
  })
  
  val rob = CircularBuffer(new RoBEntry, NumRoBEntries)

  // DU Allocation
  io.duOut.valid        := !rob.isFull
  io.duOut.bits.robIdx  := rob.tailIdx

  io.duIn.ready         := !rob.isFull
  when(io.duIn.fire) {
    rob.enq(io.duIn.bits)
    printf("[RoB] alloc new id. robIdx: %d\n", rob.tailIdx)
  }

  // CDB writeback. check robIdx.
  when(io.cdbIn.valid) {
    val payload = io.cdbIn.bits
    val entry = rob.buffer(payload.robIdx)
    entry.value   := payload.data
    entry.addr    := payload.addr
    entry.isReady := true.B
    printf("[RoB] received result. robIdx: %d, data: %d(%x), addr: %d(%x)\n", payload.robIdx, payload.data, payload.data, payload.addr, payload.addr)
  }

  object State extends ChiselEnum {
    val sIdle, sRollback = Value
  }
  val state = RegInit(State.sIdle)

  val headEntry = rob.buffer(rob.headIdx)
  val canCommit = headEntry.isReady && state === State.sIdle && !rob.isEmpty

  val isBranch      = headEntry.decInstr.isBranch
  val mispredicted  = isBranch && (headEntry.addr =/= headEntry.predPC)

  // default outputs
  io.lsqOut.valid   := false.B
  io.lsqOut.bits.robIdx := rob.headIdx
  io.predOut.valid  := false.B
  io.predOut.bits   := DontCare
  io.flOut.valid    := false.B
  io.flOut.bits.physIdx := headEntry.prePhysIdx
  io.duFlush.valid  := false.B
  io.duFlush.bits.archIdx   := 0.U
  io.duFlush.bits.prePhysIdx := 0.U
  io.flushOut.valid := false.B
  io.flushOut.bits  := DontCare

  val isTerminatedReg = RegInit(false.B)
  val actualPCReg     = Reg(Addr)

  io.isTerminated := isTerminatedReg

  // state machine: commit and rollback
  switch(state) {
    is(State.sIdle) {
      when(canCommit) {
        when(mispredicted) {
          // enter rollback state
          state := State.sRollback
          
          actualPCReg := headEntry.addr
          io.flushOut.valid := true.B
          io.flushOut.bits.targetPC := headEntry.addr
          
          // Update Predictor if it's a branch
          io.predOut.valid := isBranch
          io.predOut.bits.instrAddr := headEntry.pc
          io.predOut.bits.actualPC := headEntry.addr
          io.predOut.bits.actualTaken := false.B

          rob.deq() // this valid instr shall not be rolled back

          printf("[RoB] mispredicted. robIdx: %d, instr: %x, instrAddr: %x, wrongPC: %x, actualPC: %x\n", rob.headIdx, headEntry.decInstr.inst, headEntry.pc, headEntry.predPC, headEntry.addr)
        }.otherwise {
          // normal retirement
          io.flOut.valid := (headEntry.decInstr.rd =/= 0.U) && headEntry.decInstr.writeRf
          printf("[RoB] commit. robIdx: %d, instr: %x\n", rob.headIdx, headEntry.decInstr.inst)
          printf("[RoB] commit: isBranch = %b, actualPC = %x, predPC = %x\n", isBranch, headEntry.addr, headEntry.predPC)
          
          // valid: must wait for ready(fire), invalid: just go on.
          when(io.flOut.ready || !io.flOut.valid) {
            val isTerminate = isTerminateInst(headEntry.decInstr.inst)

            // signal LSQ if it's a store
            io.lsqOut.valid := headEntry.decInstr.isStore && !isTerminate
            io.lsqOut.bits.robIdx := rob.headIdx
            
            // Update Predictor if it's a branch
            io.predOut.valid := isBranch
            io.predOut.bits.instrAddr := headEntry.pc
            io.predOut.bits.actualPC := headEntry.value
            io.predOut.bits.actualTaken := true.B

            when(isTerminate) {
              isTerminatedReg := true.B
              printf("[RoB] Termination Triggered. ++++++++++++++++++++++++++++++++++++++\n")
            }

            rob.deq()
          }
        }
      }
    }

    is(State.sRollback) {
      when(!rob.isEmpty) {
        printf("[RoB] Try to rollback.\n")
        // Keep flushing front-end until RoB is cleared
        io.flushOut.valid := true.B
        io.flushOut.bits.targetPC := actualPCReg
      
        // Peek at the tail without popping yet to check if DU is ready
        val lastEntry = rob.back()
        
        io.duFlush.valid           := lastEntry.decInstr.writeRf
        io.duFlush.bits.archIdx    := lastEntry.decInstr.rd
        io.duFlush.bits.prePhysIdx := lastEntry.prePhysIdx

        // Only pop from tail when DU (RAT) acknowledges the recovery
        // If the instruction didn't write to RF, pop immediately
        when(io.duFlush.ready || !lastEntry.decInstr.writeRf) {
          printf("[RoB] Rollback. writeRf: %b, archIdx: %d, prePhysIdx: %d\n", lastEntry.decInstr.writeRf, lastEntry.decInstr.rd, lastEntry.prePhysIdx)
          rob.popBack()
        }
      }.otherwise {
        // All speculative instructions cleared
        state := State.sIdle
      }
    }
  }
}