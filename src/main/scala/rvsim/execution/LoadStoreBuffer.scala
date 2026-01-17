package rvsim.execution

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class LoadStoreBuffer extends Module {
  val io = IO(new Bundle {
    val duInput    = Flipped(new DUToLSB)
    val miInput    = Flipped(new MemoryResponse)
    val robInput   = Flipped(new RoBToLSB)
    val flushInput = new FlushListener
    val cdbInput   = new CDBListener
    val miOutput   = new MemoryRequest
    val cdbOutput  = new CDBSource
  })

  val lsb_entries = RegInit(VecInit(Seq.fill(Config.LSB_ENTRIES)(0.U.asTypeOf(new Bundle {
    val bits  = new LSBEntry
    val valid = Bool()
    val addrComputed = Bool()
    val fullAddr     = UInt(Config.XLEN.W)
    val executed     = Bool()
  }))))

  val enq_ptr = RegInit(0.U(log2Ceil(Config.LSB_ENTRIES).W))
  val deq_ptr = RegInit(0.U(log2Ceil(Config.LSB_ENTRIES).W))
  val count   = RegInit(0.U(log2Ceil(Config.LSB_ENTRIES + 1).W))

  val can_accept = count < Config.LSB_ENTRIES.U
  io.duInput.allocReq.ready := can_accept

  when(io.duInput.allocReq.fire) {
    lsb_entries(enq_ptr).bits  := io.duInput.allocReq.bits
    lsb_entries(enq_ptr).valid := true.B
    lsb_entries(enq_ptr).addrComputed := false.B
    lsb_entries(enq_ptr).executed     := false.B
    enq_ptr := enq_ptr + 1.U
    count   := count + 1.U
  }

  when(io.cdbInput.in.valid) {
    val cdb = io.cdbInput.in.bits
    lsb_entries.foreach { e =>
      when(e.valid) {
        when(!e.bits.addr.baseReady && e.bits.addr.baseRobIdx === cdb.robIdx) {
          e.bits.addr.base      := cdb.data
          e.bits.addr.baseReady := true.B
        }
        when(e.bits.isStore && !e.bits.storeData.ready && e.bits.robIdx === cdb.robIdx) {
          e.bits.storeData.value := cdb.data
          e.bits.storeData.ready := true.B
        }
      }
    }
  }

  lsb_entries.foreach { e =>
    when(e.valid && e.bits.addr.baseReady && !e.addrComputed) {
      e.fullAddr     := (e.bits.addr.base.asSInt + e.bits.addr.offset).asUInt
      e.addrComputed := true.B
    }
  }

  // store first
  val head_entry = lsb_entries(deq_ptr)
  val can_issue_load = head_entry.valid && head_entry.bits.isLoad && head_entry.addrComputed && !head_entry.executed
  val can_issue_store = head_entry.valid && head_entry.bits.isStore && head_entry.addrComputed && 
                        head_entry.bits.storeData.ready && io.robInput.storeCommit.valid && 
                        io.robInput.storeCommit.bits.robIdx === head_entry.bits.robIdx

  io.miOutput.req.valid := false.B
  io.miOutput.req.bits  := DontCare

  when(can_issue_load || can_issue_store) {
    io.miOutput.req.valid := true.B
    io.miOutput.req.bits.addr  := head_entry.fullAddr
    io.miOutput.req.bits.data  := head_entry.bits.storeData.value
    io.miOutput.req.bits.isWrite := head_entry.bits.isStore
    io.miOutput.req.bits.size  := head_entry.bits.size
    io.miOutput.req.bits.pc    := head_entry.bits.pc
    io.miOutput.req.bits.isInstruction := false.B
  }

  io.miInput.resp.ready := true.B
  io.cdbOutput.req.valid := false.B
  io.cdbOutput.req.bits  := DontCare

  when(io.miInput.resp.valid) {
    // load
    when(head_entry.bits.isLoad) {
      io.cdbOutput.req.valid        := true.B
      io.cdbOutput.req.bits.data    := io.miInput.resp.bits.data
      io.cdbOutput.req.bits.robIdx  := head_entry.bits.robIdx
      io.cdbOutput.req.bits.destReg := head_entry.bits.destReg
      
      head_entry.valid := false.B
      deq_ptr := deq_ptr + 1.U
      count   := count - 1.U
    }
  }

  // store
  when(io.miOutput.req.fire && head_entry.bits.isStore) {
    head_entry.valid := false.B
    deq_ptr := deq_ptr + 1.U
    count   := count - 1.U
  }

  // flush
  when(io.flushInput.req.valid) {
    lsb_entries.foreach(_.valid := false.B)
    enq_ptr := 0.U
    deq_ptr := 0.U
    count   := 0.U
  }
  io.flushInput.flushed := true.B
}