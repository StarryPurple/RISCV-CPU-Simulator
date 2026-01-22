package rvsim3

import chisel3._
import chisel3.util._
import Config._

class LSQEntry extends Bundle {
  val isWrite  = Bool()
  val mask     = UInt(4.W) // 0001: byte, 0011: half, 1111: word

  val addrReady   = Bool()
  val addr        = Addr
  val addrPhysIdx = PhysIndex
  
  val dataReady   = Bool()
  val data        = XData      // for load: temporarily store fetched data here
  val dataPhysIdx = PhysIndex  // for load: this is where data should be broadcast to in CDB. invalid for store.

  val readyToIssue = Bool()    // for load: always true. for store: initially false, and become true after RoB confirmation.
  val robIdx       = RoBIndex  // for store: RoB confirms this entry with this index.
}

class LoadStoreQueue extends Module {
  val io = IO(new Bundle {
    val duIn   = Flipped(Decoupled(new LSQEntry))
    val robIn  = Flipped(Valid(new RoBToLSQ))
    val miOut  = Decoupled(new MIReq)
    val miIn   = Flipped(Decoupled(new MIResp))
    val cdbIn  = Flipped(Valid(new CDBPayload))
    val cdbOut = Decoupled(new CDBPayload)
    val aluIn  = Flipped(Valid(new ALUToLSQ))
    val flush  = Flipped(Valid(new FlushPipeline))
  })

  val lsq = CircularBuffer(Valid(new LSQEntry), NumLSBEntries)

  // 1. DU dispatch
  io.duIn.ready := !lsq.isFull && !io.flush.valid
  when(io.duIn.fire) {
    val entry = io.duIn
    val newEntry = Wire(Valid(new LSQEntry))
    newEntry.valid := true.B
    newEntry.bits  := entry.bits
    newEntry.bits.readyToIssue := !io.duIn.bits.isWrite

    // and.. check CDB(data). ALU/RoB can't be that fast.
    when(entry.bits.isWrite && !entry.bits.dataReady && io.cdbIn.valid && entry.bits.dataPhysIdx === io.cdbIn.bits.physIdx) {
      printf("[LSQ] got data from CDB (at just in). physIdx: %d, data: %d(%x)\n", entry.bits.dataPhysIdx, io.cdbIn.bits.data, io.cdbIn.bits.data)
      newEntry.bits.data      := io.cdbIn.bits.data
      newEntry.bits.dataReady := true.B
    }

    lsq.enq(newEntry)
  }

  // 2. ALU/CDB wakeup and RoB confirmation
  for (i <- 0 until NumLSBEntries) {
    val entry = lsq.buffer(i)
    when(entry.valid && !io.flush.valid) {
      // addr (load/store)
      when(!entry.bits.addrReady && io.aluIn.valid && entry.bits.robIdx === io.aluIn.bits.robIdx) {
        printf("[LSQ] got addr from ALU. lsqIdx: %d, robIdx: %d, addrPhysIdx: %d, dataPhysIdx: %d, addr: %x\n", i.U, entry.bits.robIdx, entry.bits.addrPhysIdx, entry.bits.dataPhysIdx, io.aluIn.bits.addr)
        entry.bits.addr      := io.aluIn.bits.addr
        entry.bits.addrReady := true.B
      }
      // data (store)
      when(entry.bits.isWrite && !entry.bits.dataReady && io.cdbIn.valid && entry.bits.dataPhysIdx === io.cdbIn.bits.physIdx) {
        printf("[LSQ] got data from CDB. lsqIdx: %d, dataPhysIdx: %d, addrPhysIdx: %d, data: %d(%x)\n", i.U, entry.bits.dataPhysIdx, entry.bits.addrPhysIdx, io.cdbIn.bits.data, io.cdbIn.bits.data)
        entry.bits.data      := io.cdbIn.bits.data
        entry.bits.dataReady := true.B
      }
      // RoB confirmation (store)
      when(entry.bits.isWrite && !entry.bits.readyToIssue && io.robIn.valid && entry.bits.robIdx === io.robIn.bits.robIdx) {
        printf("[LSQ] got confirmation from RoB. lsqIdx: %d, robIdx: %d, dataPhysIdx: %d, addrPhysIdx: %d\n", i.U, entry.bits.robIdx, entry.bits.dataPhysIdx, entry.bits.addrPhysIdx)
        entry.bits.readyToIssue := true.B
      }
    }
  }

  // 3. MI issue
  val headEntry = lsq.buffer(lsq.headIdx)
  val h = headEntry.bits

  val waitResp = RegInit(false.B)

  val shallIssue = headEntry.valid && h.addrReady && h.readyToIssue && 
                 Mux(h.isWrite, h.dataReady, true.B) && !waitResp

  io.miOut.valid      := shallIssue && !io.flush.valid
  io.miOut.bits.addr  := h.addr
  io.miOut.bits.data  := h.data
  io.miOut.bits.mask  := h.mask
  io.miOut.bits.isWrite := h.isWrite

  when(io.miOut.fire) {
    waitResp := true.B 
    printf("[LSQ] waitResp. addr = %x, data = %d\n", h.addr, h.data)
  }

  // 4. MI response, CDB broadcast
  val isLoad = headEntry.valid && !h.isWrite

  // if isLoad: shall only accept data at data not accepted. Not changing waitResp until queue pop...
  io.miIn.ready := (isLoad && !h.dataReady) || !isLoad

  when(io.miIn.fire && isLoad) {
    headEntry.bits.data      := io.miIn.bits.data
    headEntry.bits.dataReady := true.B
    printf("[LSQ] Receive load data. lsqIdx: %d, robIdx: %d, data: %d(%x)\n", lsq.headIdx, headEntry.bits.robIdx, io.miIn.bits.data, io.miIn.bits.data)
  }

  // load/store altogether.
  // load: need where to load and what to broadcast. stop at fire.
  // store: need where to store and what to store. stop at fire.
  val CDBFired = RegInit(false.B)

  io.cdbOut.valid        := !CDBFired && headEntry.valid && h.dataReady && h.addrReady && !io.flush.valid
  io.cdbOut.bits.addr    := DontCare
  io.cdbOut.bits.data    := h.data
  io.cdbOut.bits.robIdx  := h.robIdx
  io.cdbOut.bits.physIdx := Mux(h.isWrite, 0.U, h.dataPhysIdx) // use x0 protection mechanic to avoid RF writing invalid data.
  
  // printf("[LSQ Tmp] %d %d %d %d %d\n", CDBFired, headEntry.valid, h.dataReady, h.addrReady, io.flush.valid)
  when(io.cdbOut.fire) {
    CDBFired := true.B
    printf("[LSQ] CDB has fired. lsqIdx: %d, robIdx: %d, physIdx: %d, data: %d\n", lsq.headIdx, h.robIdx, Mux(h.isWrite, 0.U, h.dataPhysIdx), h.data)
  }

  // 5. LSQ pop
  val shallPop = Mux(h.isWrite, 
    io.miIn.fire,      // Store: MI confirmed write.
    io.cdbOut.fire     // Load: CDB broadcast succeeded
  ) && headEntry.valid

  when((shallPop || io.flush.valid) && !lsq.isEmpty) {
    printf("[LSQ] deq one entry. idx: %d\n", lsq.headIdx)
    lsq.buffer(lsq.headIdx).valid := false.B
    lsq.deq()
    waitResp := false.B
    CDBFired := false.B
  }

  // --- 6. Flush ---
  when(io.flush.valid) {
    lsq.flush()
    waitResp := false.B
    for (i <- 0 until NumLSBEntries) lsq.buffer(i).valid := false.B
  }
}