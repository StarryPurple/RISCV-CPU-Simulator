package rvsim3

import chisel3._
import chisel3.util._
import Config._

class DecToDU extends Bundle {
  val decInstr = new DecodedInst
}

class Decoder extends Module {
  val io = IO(new Bundle {
    val ifIn  = Flipped(Decoupled(new IFToDec))
    val duOut = Decoupled(new DecToDU)
    val flush = Flipped(Valid(new FlushPipeline))
  })
  
  object State extends ChiselEnum {
    val sIdle, sBusy, sReady = Value
  }
  val state = RegInit(State.sIdle)

  val instrReg   = Reg(Inst)
  val pcReg      = Reg(Addr)
  val decodedReg = Reg(new DecodedInst)

  // IF
  io.ifIn.ready := (state === State.sIdle) && !io.flush.valid
  
  // DU
  io.duOut.valid := (state === State.sReady) && !io.flush.valid
  io.duOut.bits.decInstr := decodedReg

  switch(state) {
    is(State.sIdle) {
      when(io.ifIn.fire) {
        instrReg := io.ifIn.bits.instr
        pcReg    := io.ifIn.bits.pc
        state    := State.sBusy
      }
    }
    is(State.sBusy) {
      // 1 cycle delay.
      decodedReg := DecodedInst(instrReg, pcReg)
      state := State.sReady
    }
    is(State.sReady) {
      when(io.duOut.fire) {
        state := State.sIdle
      }
    }
  }

  when(io.flush.valid) {
    state := State.sIdle
    // just in case...
    instrReg := 0.U
    pcReg    := 0.U
    decodedReg.inst  := 0.U
    decodedReg.itype := InstrType.INVALID
  }
}