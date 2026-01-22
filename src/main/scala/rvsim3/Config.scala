package rvsim3

import chisel3._
import chisel3.util._

object Config {
  val MemSize = 1024 * 1024 * 4   // 4MB
  val NumLSBEntries = 16
  val NumRSEntries = 16

  val InstLen = 32
  val InstWidth = log2Ceil(InstLen)
  def Inst = UInt(InstLen.W)

  val XDataLen = 32
  val XDataWidth = log2Ceil(XDataLen)
  def XData = UInt(XDataLen.W)

  val AddrLen = 32
  val AddrWidth = log2Ceil(AddrLen)
  def Addr = UInt(AddrLen.W)

  val NumArchRegs = 32
  val ArchIndexLen = log2Ceil(NumArchRegs)
  def ArchIndex = UInt(ArchIndexLen.W)

  val NumRoBEntries = 16
  val RoBIndexLen = log2Ceil(NumRoBEntries)
  def RoBIndex = UInt(RoBIndexLen.W)

  val NumPhysRegs = NumArchRegs + NumRoBEntries + 16 // add a margin, in case something bad happens
  val PhysIndexLen = log2Ceil(NumPhysRegs)
  def PhysIndex = UInt(PhysIndexLen.W)
  
  val StartAddr = 0.U(AddrLen.W)
  val TerminateInst = 0xfe000fa3L // sb x0, -1(x0)
}