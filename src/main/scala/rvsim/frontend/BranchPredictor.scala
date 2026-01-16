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
  
}