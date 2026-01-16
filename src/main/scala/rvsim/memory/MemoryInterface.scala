package rvsim.memory

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class MemoryInterface extends Module {
  val io = IO(new Bundle {
    val ifInput = Flipped(new MemoryRequest)
    val lsbInput = Flipped(new MemoryRequest)
    val ifOutput = new MemoryResponse
    val lsbOutput = new MemoryResponse
  })
}