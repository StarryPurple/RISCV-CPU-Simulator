package rvsim.frontend

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class InstructionFetcher extends Module {
  val io = IO(new Bundle {
    val miInput = Flipped(new MemoryResponse)
    val predInput = Flipped(new PredToIF)
    val flushInput = new FlushListener
    val predOutput = new IFToPred
    val decOutput = new IFToDecoder
    val miOutput = new MemoryRequest
  })
  
  // pc stored in reg. start at 0x0
  val pc = RegInit(0.U(Config.XLEN.W))

  // state machine
  object State extends ChiselEnum {
    val sRequest, sWaitResp, sDecode = Value
  }
  val state = RegInit(State.sRequest)

  val currentPC = Reg(UInt(Config.XLEN.W))
  val predictedNextPC = Reg(UInt(Config.XLEN.W))

  val gotInstr = RegInit(false.B)
  val gotPred  = RegInit(false.B)
  val instrBuffer = Reg(UInt(Config.INST_WIDTH.W))

  io.predOutput.req.valid   := (state === State.sRequest)
  io.predOutput.req.bits.pc := pc
  io.predOutput.req.bits.instr := instrBuffer // might be wrong

  io.miOutput.req.valid      := (state === State.sRequest) && !io.flushInput.req.valid
  io.miOutput.req.bits.addr  := pc
  io.miOutput.req.bits.isInstruction := true.B
  io.miOutput.req.bits.size  := MemoryAccessSize.WORD
  io.miOutput.req.bits.data  := DontCare // never stores
  io.miOutput.req.bits.isWrite := false.B
  io.miOutput.req.bits.pc    := pc
  io.miOutput.req.bits.byteEnable := DontCare

  io.decOutput.req.valid := false.B
  io.decOutput.req.bits := DontCare

  switch(state) {
    is(State.sRequest) {
      when(io.miOutput.req.fire && io.predOutput.req.fire) {
        currentPC := pc
        gotInstr := false.B
        gotPred := false.B
        state := State.sWaitResp
        printf("IF: Request sent. Waiting Resp.\n")
      }
    }
    is(State.sWaitResp) {
      when(io.miInput.resp.valid) {
        instrBuffer := io.miInput.resp.bits.data
        gotInstr := true.B
      }
      when(io.predInput.resp.valid) {
        predictedNextPC := io.predInput.resp.bits.targetPC
        gotPred := true.B
      }
      when((gotInstr || io.miInput.resp.valid) && (gotPred || io.predInput.resp.valid)) {
        state := State.sDecode
        printf("IF: Resp collected. Instr is %d. Next pc is %d. Send to Decoder.\n", instrBuffer, predictedNextPC)
      }
    }
    is(State.sDecode) {
      io.decOutput.req.valid       := true.B
      io.decOutput.req.bits.instr  := instrBuffer
      io.decOutput.req.bits.pc     := currentPC
      io.decOutput.req.bits.predPC := predictedNextPC
      when(io.decOutput.req.fire) {
        pc    := predictedNextPC
        state := State.sRequest
        printf("IF: Sent to Decoder. Request next instr.\n")
      }
    }
  }

  when(io.flushInput.req.valid) {
    pc := io.flushInput.req.bits.targetPC
    state := State.sRequest
    io.flushInput.flushed := true.B
    printf("IF: flush.")
  } .otherwise {
    io.flushInput.flushed := false.B
  }

  io.miInput.resp.ready   := (state === State.sWaitResp) && !gotInstr
  io.predInput.resp.ready := (state === State.sWaitResp) && !gotPred
}