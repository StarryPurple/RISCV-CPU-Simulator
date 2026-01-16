package rvsim.frontend

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class Decoder extends Module {
  val io = IO(new Bundle {
    val ifInput = Flipped(new IFToDecoder)
    val duOutput = new DecoderToDU
  })
  
}