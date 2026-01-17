package rvsim.execution

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class ReservationStation extends Module {
  val io = IO(new Bundle {
    val duInput = Flipped(new DUToRS)
    val flushInput = new FlushListener
    val cdbInput = new CDBListener
    val aluOutput = new RSToALU
  })
  
}