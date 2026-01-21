package rvsim3

import chisel3._
import chisel3.util._
import Config._

class RoBToLSQ extends Bundle {
  val robIdx = RoBIndex
}

class RoBToPred extends Bundle {
  val instrAddr   = Addr
  val actualPC    = Addr
  val actualTaken = Bool()
}

class RoBToDU extends Bundle {

}

class RoBToLF extends Bundle {
  val physIdx = PhysIndex
}

class ReorderBuffer extends Module {
  
}