package rvsim.interfaces

import chisel3._
import chisel3.util._
import rvsim.config.Config

class MemoryRequest extends Bundle {
  val addr = UInt(Config.ADDR_WIDTH.W)
  val data = UInt(Config.XLEN.W)
  val write = Bool()
  val size = UInt(2.W) // 0: 1B, 1: 2B, 2: 4B, 3: 8B
  val tag = UInt(Config.CDB_TAG_WIDTH.W)
  val robIdx = UInt(Config.ROB_IDX_WIDTH.W)

  def isAligned(): Bool = {
    val mask = MuxLookup(size, 0.U)(Seq(
      0.U -> 0.U,
      1.U -> 1.U,
      2.U -> 3.U,
      3.U -> 7.U
    ))
    (addr & mask) === 0.U
  }

  def byteMask(): UInt = {
    val offset = addr(2, 0)
    MuxLookup(size, 0xff.U)(Seq(
      0.U -> (1.U << offset),
      1.U -> (3.U << (offset & 6.U)),
      2.U -> (7.U << (offset & 4.U)),
    ))
  }
}

class MemoryResponse extends Bundle {
  val data = UInt(Config.XLEN.W)
  val tag = UInt(Config.CDB_TAG_WIDTH.W)
  val robIdx = UInt(Config.ROB_IDX_WIDTH.W)
  val status = UInt(2.W)
  
  def isOK(): Bool = status === 0.U
}

class MemoryArbiterMemBundle extends Bundle {
  val req = Decoupled(new Bundle { // to real RAM
    val addr = UInt(Config.MEM_ADDR_WIDTH.W)
    val data = UInt(Config.MEM_DATA_WIDTH.W)
    val write = Bool()
    val mask = UInt(8.W)
  })
  val resp = Flipped(Decoupled(new Bundle { // from real RAM
    val data = UInt(Config.MEM_DATA_WIDTH.W)
  }))
}