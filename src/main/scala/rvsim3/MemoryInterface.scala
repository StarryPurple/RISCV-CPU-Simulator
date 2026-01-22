package rvsim3

import chisel3._
import chisel3.util._
import Config._

class MIToRAM extends Bundle {
  val addr    = Addr      // target address
  val wdata   = XData     // write data
  val wmask   = UInt(4.W) // 0001: byte, 0011: half, 1111: word
  val isWrite = Bool()
}

// to IF/LSB
class MIResp extends Bundle {
  val data = XData
}

// from IF/LSB
class MIReq extends Bundle {
  val addr    = Addr
  val data    = XData
  val mask    = UInt(4.W)
  val isWrite = Bool()
}

class MemoryInterface extends Module {
  val io = IO(new Bundle {
    val lsqIn  = Flipped(Decoupled(new MIReq))
    val ifIn   = Flipped(Decoupled(new MIReq))
    val lsqOut = Decoupled(new MIResp)
    val ifOut  = Decoupled(new MIResp)

    val ramIn  = Flipped(Decoupled(new RAMToMI))
    val ramOut = Decoupled(new MIToRAM)
    
    val flush  = Flipped(Valid(new FlushPipeline))
  })

  object State extends ChiselEnum {
    val sIdle, sBusy, sReady = Value
  }
  val state = RegInit(State.sIdle)

  val epoch = RegInit(false.B)
  val lastFlush = RegNext(io.flush.valid, false.B)
  when(io.flush.valid && !lastFlush) { epoch := !epoch }

  val activeReq     = Reg(new MIReq)
  val activeEpoch   = Reg(Bool())
  val activeFromLSQ = Reg(Bool())
  val rdataReg      = Reg(XData)

  // 1. sIdle

  val canAccept = state === State.sIdle && !io.flush.valid
  io.lsqIn.ready := canAccept
  io.ifIn.ready  := canAccept && !io.lsqIn.valid // only at LSQ req is not valid

  when(canAccept) {
    when(io.lsqIn.valid) {
      printf("[MI] Received LSQ req. isWrite = %b, addr: %x, data: %d(%x)\n", io.lsqIn.bits.isWrite, io.lsqIn.bits.addr, io.lsqIn.bits.data, io.lsqIn.bits.data)
      activeReq := io.lsqIn.bits
      activeFromLSQ := true.B
      activeEpoch := epoch
      when(io.lsqIn.fire) { state := State.sBusy }
    } .elsewhen(io.ifIn.valid) {
      printf("[MI] Received IF req. addr: %x\n", io.ifIn.bits.addr)
      activeReq := io.ifIn.bits
      activeFromLSQ := false.B
      activeEpoch := epoch
      when(io.ifIn.fire) { state := State.sBusy }
    }
  }

  // 2. sBusy
  io.ramOut.valid        := (state === State.sBusy)
  io.ramOut.bits.addr    := activeReq.addr
  io.ramOut.bits.wdata   := activeReq.data
  io.ramOut.bits.wmask   := activeReq.mask
  io.ramOut.bits.isWrite := activeReq.isWrite

  // lock the value when data is ready.
  io.ramIn.ready := (state === State.sBusy)

  when(state === State.sBusy) {
    when(io.ramIn.fire) {
      when(activeEpoch === epoch && !io.flush.valid) {
        printf("[MI] update rdataReg: %d(%x)\n", io.ramIn.bits.rdata, io.ramIn.bits.rdata)
        rdataReg := io.ramIn.bits.rdata
        state    := State.sReady
      } .otherwise {
        // old data / in flush
        state := State.sIdle
      }
    }
  }

  // 3. sReady
  val targetOutReady = Mux(activeFromLSQ, io.lsqOut.ready, io.ifOut.ready)
  val respValid      = state === State.sReady && !io.flush.valid

  io.lsqOut.valid     := respValid && activeFromLSQ
  io.lsqOut.bits.data := rdataReg

  io.ifOut.valid      := respValid && !activeFromLSQ
  io.ifOut.bits.data  := rdataReg

  when(state === State.sReady) {
    when(io.flush.valid) {
      state := State.sIdle
    } .elsewhen(targetOutReady) {
      printf("[MI] give data %x\n", rdataReg)
      state := State.sIdle
    }
  }

  when(io.flush.valid) {
    state := State.sIdle
  }
}