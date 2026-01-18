package rvsim2

import chisel3._
import chisel3.util._

class RegisterFile extends Module {
  val io = IO(new Bundle {
    val duInput  = Input(new DUToRF)
    val robInput = Input(new ROBToRF)
    val duOutput = Output(new RFToDU)
    
    val getReg10  = Output(UInt(Config.XLEN.W))
  })

  object State extends ChiselEnum {
    val idle, reading = Value
  }

  val regs    = RegInit(VecInit(Seq.fill(Config.RFSize)(0.U(Config.XLEN.W))))
  io.getReg10 := regs(10)
  val curStat = RegInit(State.idle)
  
  val repRi = RegInit(false.B)
  val repRj = RegInit(false.B)
  val vi    = RegInit(0.U(Config.XLEN.W))
  val vj    = RegInit(0.U(Config.XLEN.W))

  val nextStat = WireDefault(curStat)

  io.duOutput := 0.U.asTypeOf(new RFToDU)

  when(io.robInput.isValid) {
    val addr = io.robInput.dstReg
    when(addr =/= 0.U) {
      regs(addr) := io.robInput.value
    }
  }

  switch(curStat) {
    is(State.idle) {
      when(io.duInput.isValid) {
        repRi := io.duInput.reqRi
        vi    := Mux(io.duInput.Ri === 0.U, 0.U, regs(io.duInput.Ri))
        
        repRj := io.duInput.reqRj
        vj    := Mux(io.duInput.Rj === 0.U, 0.U, regs(io.duInput.Rj))
        
        nextStat := State.reading
      }
    }

    is(State.reading) {
      io.duOutput.isValid := true.B
      io.duOutput.repRi   := repRi
      io.duOutput.repRj   := repRj
      io.duOutput.Vi      := vi
      io.duOutput.Vj      := vj
      
      nextStat := State.idle
    }
  }

  curStat := nextStat
}