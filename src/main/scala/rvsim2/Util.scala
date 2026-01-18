package rvsim2

import chisel3._
import chisel3.util._

object Util {
  def signExtend(valUInt: UInt, len: Int): SInt = {
    val signBit = valUInt(len - 1)
    val extended = Cat(Fill(32 - len, signBit), valUInt(len - 1, 0))
    extended.asSInt
  }

  def signExtend(valUInt: UInt, len: Int, isOne: Bool): SInt = {
    val extended = Cat(Fill(32 - len, isOne), valUInt(len - 1, 0))
    extended.asSInt
  }

  def sliceBytes(valUInt: UInt, high: Int, low: Int): UInt = {
    valUInt(high, low)
  }

  def toSmallEndian32(bigEndian: UInt): UInt = {
    val s7_0   = bigEndian(31, 24)
    val s15_8  = bigEndian(23, 16)
    val s23_16 = bigEndian(15, 8)
    val s31_24 = bigEndian(7, 0)
    Cat(s31_24, s23_16, s15_8, s7_0)
  }

  def asSigned(valUInt: UInt): SInt = valUInt.asSInt
  def asUnsigned(valSInt: SInt): UInt = valSInt.asUInt

  def concat(left: UInt, right: UInt, rightLen: Int): UInt = {
    Cat(left, right(rightLen - 1, 0))
  }
}

class CircQueue[T <: Data](gen: T, val len: Int) {
  private val data = Reg(Vec(len, gen))
  private val head = RegInit(0.U(log2Ceil(len).W))
  private val tail = RegInit(0.U(log2Ceil(len).W))
  private val isFull = RegInit(false.B)

  def empty: Bool = (head === tail) && !isFull
  def full: Bool  = isFull

  def size: UInt = {
    Mux(isFull, len.U,
      Mux(tail >= head, tail - head, len.U + tail - head)
    )
  }

  def push(t: T): Unit = {
    when(!full) {
      data(tail) := t
      val nextTail = Mux(tail === (len - 1).U, 0.U, tail + 1.U)
      tail := nextTail
      when(nextTail === head) {
        isFull := true.B
      }
    }
  }

  def pop(): Unit = {
    when(!empty) {
      head := Mux(head === (len - 1).U, 0.U, head + 1.U)
      isFull := false.B
    }
  }

  def popBack(): Unit = {
    when(!empty) {
      tail := Mux(tail === 0.U, (len - 1).U, tail - 1.U)
      isFull := false.B
    }
  }

  def front: T = data(head)
  
  def back: T = {
    val prevTail = Mux(tail === 0.U, (len - 1).U, tail - 1.U)
    data(prevTail)
  }

  def frontIndex: UInt = head
  
  def backIndex: UInt = Mux(tail === 0.U, (len - 1).U, tail - 1.U)

  def nextIndex: UInt = tail

  def at(index: UInt): T = data(index)

  def clear(): Unit = {
    head := 0.U
    tail := 0.U
    isFull := false.B
  }

  def writeAt(index: UInt, t: T): Unit = {
    data(index) := t
  }
}

object CircQueue {
  def apply[T <: Data](gen: T, len: Int): CircQueue[T] = new CircQueue(gen, len)
}