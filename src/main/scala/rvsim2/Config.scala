package rvsim2

import chisel3._
import chisel3.util._

object Config {
  val XLEN = 32
  
  val RAMSize = 1 << 20
  val IFUSize = 8
  val ROBSize = 16
  val LSBSize = 16
  val RSSize  = 16
  val RFSize  = 32
  
  def DataT = UInt(XLEN.W)
  def InstrT = UInt(32.W)
  def AddrT = UInt(32.W)
  
  val ROBIdxWidth = 4.W
  val RFIdxWidth  = 5.W
  val LenWidth    = 3.W
  
  val StartAddr = 0

  val TerminationInstr = 0xfe000fa3L // sb x0, -1(x0)
}