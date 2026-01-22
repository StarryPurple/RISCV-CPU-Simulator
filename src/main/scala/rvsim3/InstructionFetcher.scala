package rvsim3

import chisel3._
import chisel3.util._
import Config._

class IFToPred extends Bundle {
  val pc = Addr
}

class IFToDec extends Bundle {
  val instr  = Inst
  val pc     = Addr
  val predPC = Addr
}

class InstructionFetcher extends Module {
  val io = IO(new Bundle {
    val predIn  = Flipped(Decoupled(new PredToIF))
    val predOut = Decoupled(new IFToPred)
    val miIn    = Flipped(Decoupled(new MIResp))
    val miOut   = Decoupled(new MIReq)
    val decOut  = Decoupled(new IFToDec)
    val flush   = Flipped(Valid(new FlushPipeline))
  })
  
  val pc = RegInit(StartAddr)
  val isTerminated = RegInit(false.B)

  object State extends ChiselEnum {
    val sIdle, sBusy, sPred, sReady = Value
  }
  val state = RegInit(State.sIdle)

  val curInstr = Reg(Inst)
  val curPC    = Reg(Addr)
  val predPC   = Reg(Addr)

  // pre-decode
  val opcode  = curInstr(6, 0)
  val isJump  = (opcode === "b1101111".U) || (opcode === "b1100111".U) || (opcode === "b1100011".U)
  val isHalt  = isTerminateInst(curInstr)

  // MI
  val canFetch = (state === State.sIdle) && !isTerminated && !io.flush.valid
  io.miOut.valid      := canFetch
  io.miOut.bits.addr  := pc
  io.miOut.bits.data  := 0.U
  io.miOut.bits.mask  := "b1111".U
  io.miOut.bits.isWrite := false.B

  // Pred
  io.predOut.valid    := (state === State.sPred) && isJump && !isHalt
  io.predOut.bits.pc  := curPC

  // Decoder
  io.decOut.valid       := (state === State.sReady) && !io.flush.valid
  io.decOut.bits.instr  := curInstr
  io.decOut.bits.pc     := curPC
  io.decOut.bits.predPC := predPC

  // default
  io.predIn.ready := false.B
  io.miIn.ready   := false.B

  switch(state) {
    is(State.sIdle) {
      when(io.miOut.fire) {
        curPC := pc
        state := State.sBusy
      }
    }
    is(State.sBusy) {
      printf("[IF] busy, curPC: %d(%x)\n", predPC, predPC)
      io.miIn.ready := true.B
      when(io.miIn.fire) {
        curInstr := io.miIn.bits.data
        state := State.sPred
        when(io.miIn.bits.data === 0.U) {
          // instr out of bound. stop.
          printf("[IF] PC out of bound. Stop. ========================================\n")
          isTerminated := true.B
          state := State.sIdle
        }
      }
    }
    is(State.sPred) {
      printf("[IF] pred, curInstr: %x\n", curInstr)
      when(isJump) {
        io.predIn.ready := true.B
        when(io.predIn.fire) {
          predPC := io.predIn.bits.predPC
          pc     := io.predIn.bits.predPC
          state  := State.sReady
        }
      } .otherwise {
        predPC := curPC + 4.U
        pc     := curPC + 4.U
        state  := State.sReady
      }
    }
    is(State.sReady) {
      printf("[IF] ready, curInstr: %x, nextPC: %d(%x)\n", curInstr, predPC, predPC)
      when(io.decOut.fire) {
        when(isHalt) {
          printf("[IF] Termination Triggered. ---------------------------------------------------\n")
          isTerminated := true.B
        }
        state := State.sIdle
      }
    }
  }

  when(io.flush.valid) {
    state        := State.sIdle
    isTerminated := false.B
    pc           := io.flush.bits.targetPC
    printf("[IF] pc reset to %x\n", io.flush.bits.targetPC)
    when(isTerminated) {
      printf("[IF] termination cancelled. xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n")
    }
  }
}