package rvsim.execution

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class ExecuteUnit extends Module {
  val io = IO(new Bundle {
    val rsInput = Flipped(new DUToRS)
    val cdbOutput = new CDBSource
  })
  
}