package rvsim3

import chisel3._
import chisel3.util._
import Config._

class FLToDU extends Bundle {
  val physIdx = PhysIndex
}

class FreeList extends Module {
  val io = IO(new Bundle {
    val duIn  = Flipped(Decoupled(new DUToFL))
    val duOut = Decoupled(new FLToDU)
    val robIn = Flipped(Decoupled(new RoBToFL))
  })

  val numFreeRegs = NumPhysRegs - NumArchRegs
  val initialPhysRegs = (NumArchRegs until NumPhysRegs).map(_.U(log2Ceil(NumPhysRegs).W))

  val queue = new CircularBuffer(UInt(log2Ceil(NumPhysRegs).W), numFreeRegs, Some(initialPhysRegs))

  // DU
  io.duIn.ready  := !queue.isEmpty
  io.duOut.valid := !queue.isEmpty
  io.duOut.bits.physIdx := queue.buffer(queue.headIdx)

  when(io.duIn.fire) {
    queue.deq()
  }

  // RoB
  io.robIn.ready := !queue.isFull
  
  when(io.robIn.fire) {
    queue.enq(io.robIn.bits.physIdx)
  }
}