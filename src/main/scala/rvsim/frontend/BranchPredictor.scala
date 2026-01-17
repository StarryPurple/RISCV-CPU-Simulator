package rvsim.frontend

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class BranchPredictor extends Module {
  val io = IO(new Bundle {
    val ifInput = Flipped(new IFToPred)
    val robInput = Flipped(new RoBToPred)
    val ifOutput = new PredToIF
  })

  // simple BTB and BHT
  val btb = Mem(1024, UInt(Config.XLEN.W))
  val bht = Mem(1024, UInt(2.W))
  
  val pc = io.ifInput.req.bits.pc
  val index = pc(11, 2) // hashmap index

  val predTaken  = bht.read(index) >= 2.U
  val targetAddr = btb.read(index)

  io.ifOutput.resp.valid := true.B
  io.ifOutput.resp.bits.predTaken := predTaken
  io.ifOutput.resp.bits.targetPC     := Mux(predTaken, targetAddr, pc + 4.U)

  when(io.robInput.info.valid) {
    val trainPC    = io.robInput.info.bits.pc
    val trainIndex = trainPC(11, 2)
    val actualTaken = io.robInput.info.bits.taken
    val actualTarget = io.robInput.info.bits.target
    btb.write(trainIndex, actualTarget)
    val currentState = bht.read(trainIndex)
    val nextState = Mux(actualTaken, Mux(currentState === 3.U, 3.U, currentState + 1.U), Mux(currentState === 0.U, 0.U, currentState - 1.U))
    bht.write(trainIndex, nextState)
  }
}