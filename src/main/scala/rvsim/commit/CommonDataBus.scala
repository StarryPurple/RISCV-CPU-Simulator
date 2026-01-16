package rvsim.commit

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class CommonDataBus extends Module {
  val io = IO(new Bundle {
    val duInput = Flipped(new CDBSource)
    val lsbInput = Flipped(new CDBSource)
    val output = Flipped(new CDBListener)
  })

}