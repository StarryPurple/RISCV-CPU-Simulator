package rvsim3

import chisel3._
import chisel3.util._
import Config._

class RAMToMI extends Bundle {
  val rdata = XData     // read data
}

class PreloadBus extends Bundle {
  val en    = Input(Bool())
  val addr  = Input(Addr)
  val data  = Input(XData)
}

class RandomAccessMemory extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(new MIToRAM))
    val out = Decoupled(new RAMToMI)
    val preload = Input(new PreloadBus)
  })
  
  require(AddrLen == 32, "AddrLen only support 32 now")
  require(MemSize % 4 == 0, "Memory size must be divided by 4")

  val mem = SyncReadMem(MemSize / 4, Vec(4, UInt(8.W)))

  object State extends ChiselEnum {
    val sIdle, sBusy, sReady = Value
  }
  val state = RegInit(State.sIdle)

  val rdataReg = Reg(XData)
  val isWrite  = Reg(Bool())

  when(io.preload.en) {
    val pAddr = io.preload.addr(31, 2)
    val pData = VecInit(Seq.tabulate(4)(i => io.preload.data(8 * i + 7, 8 * i)))
    mem.write(pAddr, pData, "b1111".U.asBools)
  }

  io.in.ready := (state === State.sIdle) && !io.preload.en
  
  io.out.valid := (state === State.sReady)
  io.out.bits.rdata := rdataReg

  switch(state) {
    is(State.sIdle) {
      when(io.in.fire) {
        val wordAddr = io.in.bits.addr(31, 2)
        isWrite := io.in.bits.isWrite
        
        when(io.in.bits.isWrite) {
          val wdataVec = VecInit(Seq.tabulate(4)(i => io.in.bits.wdata(8 * i + 7, 8 * i)))
          mem.write(wordAddr, wdataVec, io.in.bits.wmask.asBools)
          rdataReg := 0.U // invalid though unused value
        } .otherwise {
          rdataReg := mem.read(wordAddr, true.B).asUInt
        }
        state := State.sBusy
      }
    }

    is(State.sBusy) {
      // for read: mem.read has updated in this cycle, together with rdataReg.
      // for write: Assume nothing bad happens.
      state := State.sReady
    }

    is(State.sReady) {
      when(io.out.fire) {
        state := State.sIdle
      }
    }
  }
}