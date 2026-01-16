package rvsim.execution

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class LoadStoreBuffer extends Module {
  val io = IO(new Bundle {
    val duInput = Flipped(new DUToLSB)
    val miInput = Flipped(new MemoryResponse)
    val robInput = Flipped(new RoBToLSB)
    val flushInput = new FlushListener
    val miOutput = new MemoryRequest
    val cdbOutput = new CDBSource
  })
  
}