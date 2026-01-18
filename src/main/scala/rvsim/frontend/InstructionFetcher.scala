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
  
  val pc = RegInit(0.U(Config.XLEN.W))

  object State extends ChiselEnum {
    val sRequest, sWaitResp, sDecode = Value
  }
  val state = RegInit(State.sRequest)

  val currentPC = Reg(UInt(Config.XLEN.W))
  val predictedNextPC = Reg(UInt(Config.XLEN.W))

  val gotInstr = RegInit(false.B)
  val gotPred  = RegInit(false.B)
  val instrBuffer = Reg(UInt(Config.INST_WIDTH.W))

  io.predOutput.req.valid   := (state === State.sRequest) && !io.flushInput.req.valid
  io.predOutput.req.bits.pc := pc
  io.predOutput.req.bits.instr := 0.U

  io.miOutput.req.valid      := (state === State.sRequest) && !io.flushInput.req.valid
  io.miOutput.req.bits.addr   := pc
  io.miOutput.req.bits.isInstruction := true.B
  io.miOutput.req.bits.size   := MemoryAccessSize.WORD
  io.miOutput.req.bits.data   := DontCare
  io.miOutput.req.bits.isWrite := false.B
  io.miOutput.req.bits.pc    := pc
  io.miOutput.req.bits.byteEnable := DontCare

  io.decOutput.req.valid      := (state === State.sDecode) && !io.flushInput.req.valid
  io.decOutput.req.bits.instr := instrBuffer
  io.decOutput.req.bits.pc    := currentPC
  io.decOutput.req.bits.predPC := predictedNextPC

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
      val miFire = io.miInput.resp.fire
      val predFire = io.predInput.resp.fire

      when(miFire) {
        instrBuffer := io.miInput.resp.bits.data
        gotInstr := true.B
      }
      when(predFire) {
        predictedNextPC := io.predInput.resp.bits.targetPC
        gotPred := true.B
      }

      val allReady = (gotInstr || miFire) && (gotPred || predFire)
      when(allReady) {
        state := State.sDecode
        val finalInstr = Mux(miFire, io.miInput.resp.bits.data, instrBuffer)
        printf("IF: Resp collected. Instr is %x. Next pc is %x. Moving to Decode.\n", finalInstr, predictedNextPC)
        when(finalInstr === Config.TERMINATE_INSTR.U) {
          printf("IF: reached terminate instr ----------------!!!---------------\n")
        }
      }
    }
    is(State.sDecode) {
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
    gotInstr := false.B
    gotPred := false.B
    io.flushInput.flushed := true.B
  } .otherwise {
    io.flushInput.flushed := false.B
  }

  io.miInput.resp.ready   := (state === State.sWaitResp) && !gotInstr
  io.predInput.resp.ready := (state === State.sWaitResp) && !gotPred

  // printf("DEBUG_IF: ReqAddr=%x | RespData=%x | State=%d | gotInstr=%d\n", pc, io.miInput.resp.bits.data, state.asUInt, gotInstr)
}