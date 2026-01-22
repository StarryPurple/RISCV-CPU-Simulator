package rvsim3

import chisel3._
import chisel3.util._
import Config._

class FlushPipeline extends Bundle {
  val targetPC = Addr
}