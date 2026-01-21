package rvsim3

import chisel3._
import chisel3.util._
import Config._

class IFToPred extends Bundle {
  val pc = Addr
}

class InstructionFetcher extends Module {
  
}