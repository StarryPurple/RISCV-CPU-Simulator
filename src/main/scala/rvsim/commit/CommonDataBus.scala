package rvsim.commit

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class CommonDataBus extends Module {
  val io = IO(new Bundle {
    val aluInput = Flipped(new CDBSource)
    val lsbInput = Flipped(new CDBSource)
    val output = Flipped(new CDBListener)
  })

  val arb = Module(new Arbiter(new CDBEntry, 2))
  arb.io.in(0) <> io.lsbInput.req
  arb.io.in(1) <> io.aluInput.req

  io.output.in.valid := arb.io.out.valid
  io.output.in.bits  := arb.io.out.bits

  when(io.output.in.valid) {
    printf(p"CDB: Broadcasting: ${io.output.in.bits}\n")
  }

  arb.io.out.ready := true.B
}