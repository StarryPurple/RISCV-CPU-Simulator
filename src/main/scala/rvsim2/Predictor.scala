package rvsim2

import chisel3._
import chisel3.util._

class Predictor(val bhtSize: Int = 128, val rasSize: Int = 128) extends Module {
  val io = IO(new Bundle {
    val ifuInput  = Input(new IFUToPred)
    val robInput  = Input(new ROBToPred)
    val ifuOutput = Output(new PredToIFU)
  })

  object State extends ChiselEnum {
    val idle, predicting = Value
  }

  val state  = RegInit(State.idle)
  val predPc = RegInit(0.U(Config.XLEN.W))

  val bht = Mem(bhtSize, UInt(3.W))
  val ras = Mem(rasSize, UInt(Config.XLEN.W))

  def getHash(addr: UInt, size: Int): UInt = {
    addr(log2Ceil(size) + 1, 2)
  }

  io.ifuOutput := 0.U.asTypeOf(new PredToIFU)

  when(io.robInput.isValid) {
    val learnAddr = io.robInput.instrAddr
    val learnHash = getHash(learnAddr, bhtSize)
    
    when(io.robInput.isBr) {
      val oldCounter = bht.read(learnHash)
      val newCounter = Wire(UInt(3.W))
      
      when(io.robInput.isPredTaken) {
        newCounter := Mux(oldCounter === 7.U, 7.U, oldCounter + 1.U)
      } .otherwise {
        newCounter := Mux(oldCounter === 0.U, 0.U, oldCounter - 1.U)
      }
      bht.write(learnHash, newCounter)
    }
    ras.write(getHash(learnAddr, rasSize), io.robInput.realPc)
  }

  switch(state) {
    is(State.idle) {
      when(io.ifuInput.isValid) {
        val addr = io.ifuInput.instrAddr
        val hash = getHash(addr, bhtSize)
        val defaultPc = addr + 4.U
        val targetPc = ras.read(getHash(addr, rasSize))
        
        val finalPredPc = WireDefault(defaultPc)

        when(io.ifuInput.isBr) {
          val bhtState = bht.read(hash)
          when(bhtState >= 4.U) {
            finalPredPc := targetPc
          }
        } .elsewhen(io.ifuInput.isJalr) {
          finalPredPc := targetPc
        }

        predPc := finalPredPc
        state := State.predicting
      }
    }
    is(State.predicting) {
      io.ifuOutput.isValid := true.B
      io.ifuOutput.predPc  := predPc
      state := State.idle
    }
  }
}