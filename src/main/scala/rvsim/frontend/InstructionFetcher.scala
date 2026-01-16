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
  
}