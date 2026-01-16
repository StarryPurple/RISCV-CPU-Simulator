package rvsim.commit

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class ReorderBuffer extends Module {
  val io = IO(new Bundle {
    val predOutput = new RoBToPred
    val rfOutput = new RoBToRF
    val duOutput = new RoBToDU
    val lsbOutput = new RoBToLSB
    val flushOutput = new FlushAnnouncer
    val duInput = Flipped(new DUToRoB)
    val cdbInput = new CDBListener
  })
}