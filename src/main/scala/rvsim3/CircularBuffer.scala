package rvsim3

import chisel3._
import chisel3.util._

class CircularBuffer[T <: Data](gen: T, val entries: Int) {
  require(entries > 0 && (entries & (entries - 1)) == 0, s"Entries shall be a power of 2 (got $entries)")
  val indexWidth = log2Ceil(entries)

  val buffer = Reg(Vec(entries, gen))
  // extra bit for color: 00, 01, ..., 0N, 10, 11, ..., 1N, 00
  val head = RegInit(0.U((indexWidth + 1).W))
  val tail = RegInit(0.U((indexWidth + 1).W))

  def headIdx = head(indexWidth - 1, 0)
  def tailIdx = tail(indexWidth - 1, 0)

  def isEmpty = head === tail
  def isFull  = (headIdx === tailIdx) && (head(indexWidth) =/= tail(indexWidth))

  def enq(data: T) = {
    // assert(!isFull, "CircularBuffer: Attempted to enqueue into a full buffer")
    buffer(tailIdx) := data
    tail := tail + 1.U
  }
  def deq(): T = {
    // assert(!isEmpty, "CircularBuffer: Attempted to dequeue from an empty buffer")
    val data = WireDefault(buffer(headIdx))
    head := head + 1.U
    data
  }
  def apply(idx: UInt): T = buffer(idx)

  // for rollback
  def getAndPopBack(): T = {
    // assert(!isEmpty, "CircularBuffer: Attempted to get and pop back from an empty buffer")
    val lastIdx = (tail - 1.U)(indexWidth - 1, 0)
    val data = WireDefault(buffer(lastIdx))
    tail := tail - 1.U
    data
  }
  def flush() = {
    head := 0.U
    tail := 0.U
  }
}

object CircularBuffer {
  def apply[T <: Data](gen: T, entries: Int): CircularBuffer[T] = {
    new CircularBuffer(gen, entries)
  }
}