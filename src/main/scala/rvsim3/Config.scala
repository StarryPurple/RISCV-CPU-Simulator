package rvsim3

import chisel3.util.log2Ceil
import chisel3.UInt

object Config {
  val XLen = 32 // memory data length

  type InstT = UInt
  val InstLen = XLen
  val InstWidth = log2Ceil(InstLen)

  type DataT = UInt
  val DataLen = XLen
  val DataWidth = log2Ceil(DataLen)

  type AddrT = UInt
  val AddrLen = 32
  val AddrWidth = log2Ceil(AddrLen)

  val NumArchRegs = 32

  val NumRoBEntries = 16
  val RoBIndexT = UInt
  val RobIndexWidth = log2Ceil(NumRoBEntries)

  val NumPhysRegs = NumArchRegs + NumRoBEntries + 16 // add a margin, in case something bad happens
  val PhysIndexT = UInt
  val PhysIndexWidth = log2Ceil(NumPhysRegs)
}