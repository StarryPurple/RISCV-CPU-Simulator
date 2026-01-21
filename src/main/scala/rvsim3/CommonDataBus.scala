package rvsim3

import chisel3._
import chisel3.util._
import rvsim3.Config._

class CDBPayload extends Bundle {
  val data    = XData
  val physIdx = PhysIndex
  val robIdx  = RoBIndex
}

class CommonDataBus(val numSources: Int) extends Module {
  val io = IO(new Bundle {
    val in = Vec(numSources, Flipped(Decoupled(new CDBPayload)))
    val out = Valid(new CDBPayload)
  })
  val arbiter = Module(new RRArbiter(new CDBPayload, numSources))
  for(i <- 0 until numSources) {
    arbiter.io.in(i) <> io.in(i)
  }
  arbiter.io.out.ready := true.B // not waiting
  io.out.valid := arbiter.io.out.valid
  io.out.bits  := arbiter.io.out.bits
}