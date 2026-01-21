package rvsim3

import chisel3._
import chisel3.util._
import Config._

class LSQEntry extends Bundle {
  val isWrite  = Bool()
  val mask     = UInt(4.W) // 0001: byte, 0011: half, 1111: word

  val addrReady  = Bool()
  val addr       = Addr
  val addrRobIdx = RoBIndex
  
  val dataReady  = Bool()
  val data       = XData       // for load: temporarily store fetched data here
  val dataRobIdx = RoBIndex    // for load: this is where data should be broadcast to in CDB.

  val readyToIssue = Bool()    // for load: always true. for store: initially false, and become true after RoB confirmation.
  val storeRobIdx  = RoBIndex  // for store: RoB confirms this entry with this index.
  val loadPhysIdx  = PhysIndex // for load: this is where data shoule be written to in RF.
}

class LoadStoreQueue(numLSBEntry: Int) extends Module {
  val io = IO(new Bundle {
    val duIn   = Flipped(Decoupled(new LSQEntry))
    val robIn  = Flipped(Valid(new RoBToLSQ))
    val miOut  = Decoupled(new MIReq)
    val miIn   = Flipped(Decoupled(new MIResp))
    val cdbIn  = Flipped(Valid(new CDBPayload))
    val cdbOut = Decoupled(new CDBPayload)
    val flush  = Flipped(Valid(new FlushPipeline))
  })

  val lsq = CircularBuffer(Valid(new LSQEntry), numLSBEntry)

  // 1. DU dispatch
  io.duIn.ready := !lsq.isFull && !io.flush.valid
  when(io.duIn.fire) {
    val newEntry = Wire(Valid(new LSQEntry))
    newEntry.valid := true.B
    newEntry.bits  := io.duIn.bits
    newEntry.bits.readyToIssue := !io.duIn.bits.isWrite
    lsq.enq(newEntry)
  }

  // 2. CDB wakeup and RoB confirmation
  for (i <- 0 until numLSBEntry) {
    val entry = lsq.buffer(i)
    when(entry.valid) {
      // addr (load/store)
      when(!entry.bits.addrReady && io.cdbIn.valid && entry.bits.addrRobIdx === io.cdbIn.bits.robIdx) {
        entry.bits.addr      := io.cdbIn.bits.data
        entry.bits.addrReady := true.B
      }
      // data (store)
      when(entry.bits.isWrite && !entry.bits.dataReady && io.cdbIn.valid && entry.bits.dataRobIdx === io.cdbIn.bits.robIdx) {
        entry.bits.data      := io.cdbIn.bits.data
        entry.bits.dataReady := true.B
      }
      // RoB confirmation (store)
      when(entry.bits.isWrite && !entry.bits.readyToIssue && io.robIn.valid && entry.bits.storeRobIdx === io.robIn.bits.robIdx) {
        entry.bits.readyToIssue := true.B
      }
    }
  }

  // 3. MI issue
  val headEntry = lsq.buffer(lsq.headIdx)
  val h = headEntry.bits

  val waitResp = RegInit(false.B) // no need to write a 2-state state machine explicitly.

  val shallIssue = headEntry.valid && h.addrReady && h.readyToIssue && 
                 Mux(h.isWrite, h.dataReady, true.B) && !waitResp

  io.miOut.valid      := shallIssue && !io.flush.valid
  io.miOut.bits.addr  := h.addr
  io.miOut.bits.data  := h.data
  io.miOut.bits.mask  := h.mask
  io.miOut.bits.isWrite := h.isWrite

  when(io.miOut.fire) { waitResp := true.B }

  // 4. MI response, CDB broadcast
  val isLoad = headEntry.valid && !h.isWrite

  when(io.miIn.valid && isLoad) {
    headEntry.bits.data      := io.miIn.bits.data
    headEntry.bits.dataReady := true.B
  }

  io.cdbOut.valid        := isLoad && h.dataReady && !io.flush.valid
  io.cdbOut.bits.data    := h.data
  io.cdbOut.bits.robIdx  := h.dataRobIdx
  io.cdbOut.bits.physIdx := h.loadPhysIdx

  // if isLoad: shall only accept data at data not accepted. Not changing waitResp until queue pop...
  io.miIn.ready := (isLoad && !h.dataReady) || !isLoad

  // 5. LSQ pop
  val shallPop = Mux(h.isWrite, 
    io.miIn.valid,     // Store: MI confirmed write
    io.cdbOut.fire     // Load: CDB broadcast succeeded
  )

  when((shallPop || io.flush.valid) && !lsq.isEmpty) {
    val curIdx = lsq.headIdx
    lsq.deq()
    lsq.buffer(curIdx).valid := false.B
    waitResp := false.B
  }

  // --- 6. Flush ---
  when(io.flush.valid) {
    lsq.flush()
    waitResp := false.B
    for (i <- 0 until numLSBEntry) lsq.buffer(i).valid := false.B
  }
}