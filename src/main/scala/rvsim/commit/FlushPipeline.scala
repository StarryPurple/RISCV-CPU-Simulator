package rvsim.commit

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class FlushPipeline extends Module {
  val io = IO(new Bundle {
    val robInput = Flipped(new FlushAnnouncer)
    val ifOutput = Flipped(new FlushListener)
    val lsbOutput = Flipped(new FlushListener)
    val rsOutput = Flipped(new FlushListener)
    val duOutput = Flipped(new FlushListener)
  })
}