package rvsim.commit

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class RegisterFile extends Module {
  val io = IO(new Bundle {
    val duInput = Flipped(new DUToRF)
    val robInput = Flipped(new RoBToRF)
    val duOutput = new RFToDU
  })
}
