package rvsim3

import chisel3._
import chisel3.util._
import Config._

class FLToDU extends Bundle {
  val physIdx = PhysIndex
}

class FreeList(numPhysRegs: Int) extends Module {
  val io = IO(new Bundle {
    val duIn  = Flipped(Decoupled(new DUToFL))
    val duOut = Decoupled(new FLToDU)
    val robIn = Flipped(Decoupled(new RoBToLF))
  })

  val numFreeRegs = numPhysRegs - NumArchRegs
  val initialPhysRegs = (NumArchRegs until numPhysRegs).map(_.U(log2Ceil(numPhysRegs).W))

  val queue = new CircularBuffer(UInt(log2Ceil(numPhysRegs).W), numFreeRegs, Some(initialPhysRegs))

  // DU
  val canAlloc = !queue.isEmpty
  io.duIn.ready := canAlloc
  
  io.duOut.valid        := io.duIn.valid && canAlloc
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