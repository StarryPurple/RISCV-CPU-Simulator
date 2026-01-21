package rvsim3

import chisel3._
import chisel3.util._
import Config._

class DUToRoB extends Bundle {

}

class DUToFL extends Bundle {
  // empty: No additional information needed to attach
}

class DUToRS extends Bundle {

}

class DispatchUnit extends Module {
  val io = IO(new Bundle {
    val decIn  = Flipped(Decoupled(new DecToDU))
    val robOut = Decoupled(new DUToRoB)
    val robIn  = Flipped(Decoupled(new RoBToDU))
    val flOut  = Decoupled(new DUToFL)
    val flIn   = Flipped(Decoupled(new FLToDU))
    val lsqOut = Decoupled(new LSQEntry)
    val rsOut  = Decoupled(new RSEntry)
  })
}