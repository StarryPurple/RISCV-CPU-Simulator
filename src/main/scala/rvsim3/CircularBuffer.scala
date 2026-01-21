package rvsim3

import chisel3._
import chisel3.util._
class CircularBuffer[T <: Data](gen: T, val entries: Int, initData: Option[Seq[T]] = None) {
  require(entries > 0 && (entries & (entries - 1)) == 0, s"Entries shall be a power of 2 (got $entries)")
  val indexWidth = log2Ceil(entries)

  val buffer = initData match {
    case Some(data) => 
      require(data.length == entries, "Initial data size must match buffer entries")
      RegInit(VecInit(data))
    case None => 
      Reg(Vec(entries, gen))
  }

  val head = RegInit(0.U((indexWidth + 1).W))
  val tail = initData match {
    case Some(_) => RegInit(entries.U((indexWidth + 1).W))
    case None    => RegInit(0.U((indexWidth + 1).W))
  }

  def headIdx = head(indexWidth - 1, 0)
  def tailIdx = tail(indexWidth - 1, 0)

  def isEmpty = head === tail
  def isFull  = (headIdx === tailIdx) && (head(indexWidth) =/= tail(indexWidth))

  def enq(data: T) = {
    buffer(tailIdx) := data
    tail := tail + 1.U
  }
  
  def deq(): T = {
    val data = WireDefault(buffer(headIdx))
    head := head + 1.U
    data
  }
  
  def apply(idx: UInt): T = buffer(idx)

  def getAndPopBack(): T = {
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
  def apply[T <: Data](gen: T, entries: Int, initData: Option[Seq[T]] = None): CircularBuffer[T] = {
    new CircularBuffer(gen, entries, initData)
  }
}