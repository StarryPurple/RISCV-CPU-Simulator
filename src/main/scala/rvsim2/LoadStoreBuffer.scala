package rvsim2

import chisel3._
import chisel3.util._

class LoadStoreBuffer(val bufSize: Int = 16) extends Module {
  val io = IO(new Bundle {
    val miuInput   = Input(new MIUToLSB)
    val duInput    = Input(new DUToLSB)
    val robInput   = Input(new ROBToLSB)
    val flushInput = Input(new FlushPipeline)
    val cdbInput   = Input(new CDBOut)
    
    val robOutput  = Output(new LSBToROB)
    val miuOutput  = Output(new LSBToMIU)
    val cdbOutput  = Output(new LSBToCDB)
  })

  class Entry extends Bundle {
    val isValid     = Bool()
    val isLoad      = Bool()
    val isStore     = Bool()
    val dataLen     = UInt(3.W)
    val robIndex    = UInt(Config.ROBIdxWidth)
    val addrReady   = Bool()
    val addrValue   = UInt(Config.XLEN.W)
    val dataReady   = Bool()
    val dataIndex   = UInt(Config.ROBIdxWidth)
    val dataValue   = UInt(Config.XLEN.W)
    val isExecuted  = Bool()
    val isCommitted = Bool()
    val isFinished  = Bool()
  }

  val entries = CircQueue(new Entry, bufSize)
  val loadSent = RegInit(false.B)
  val storeSent = RegInit(false.B)
  val loadIdxReg = Reg(UInt(log2Ceil(bufSize).W))

  io.robOutput := 0.U.asTypeOf(new LSBToROB)
  io.miuOutput := 0.U.asTypeOf(new LSBToMIU)
  io.cdbOutput := 0.U.asTypeOf(new LSBToCDB)

  // CDB & ROB Monitoring
  for (i <- 0 until bufSize) {
    val entry = entries.at(i.U)
    val updatedEntry = WireDefault(entry)
    val changed = WireDefault(false.B)

    // Snooping
    val aluCDB = io.cdbInput.aluEntry
    val lsbCDB = io.cdbInput.lsbEntry

    when(entry.isValid) {
      val canUpdateData = (entry.isStore && !entry.dataReady)
      when(aluCDB.isValid) {
        when(canUpdateData && entry.dataIndex === aluCDB.robIndex) { 
          updatedEntry.dataValue := aluCDB.value; updatedEntry.dataReady := true.B; changed := true.B 
        }
        when(!entry.addrReady && entry.robIndex === aluCDB.robIndex) { 
          updatedEntry.addrValue := aluCDB.value; updatedEntry.addrReady := true.B; changed := true.B 
        }
      }
      when(lsbCDB.isValid && canUpdateData && entry.dataIndex === lsbCDB.robIndex) {
        updatedEntry.dataValue := lsbCDB.value; updatedEntry.dataReady := true.B; changed := true.B
      }
      when(io.robInput.isValid && entry.robIndex === io.robInput.robIndex) {
        updatedEntry.isCommitted := true.B
        if (true) {
          when(entry.isLoad) { updatedEntry.isFinished := true.B }
        }
        changed := true.B
      }
    }
    when(changed) { entries.writeAt(i.U, updatedEntry) }
  }

  // Load Execution Logic
  val canIssueLoad = Wire(Vec(bufSize, Bool()))
  val forwardData  = Wire(Vec(bufSize, UInt(Config.XLEN.W)))
  val canForward   = Wire(Vec(bufSize, Bool()))

  for (i <- 0 until bufSize) {
    val idx = (entries.frontIndex + i.U) % bufSize.U
    val entry = entries.at(idx)
    
    canIssueLoad(i) := false.B
    forwardData(i)  := 0.U
    canForward(i)   := false.B

    when(i.U < entries.size && entry.isLoad && !entry.isExecuted) {
      val hasReliance = WireDefault(false.B)
      val fData       = WireDefault(0.U(Config.XLEN.W))
      val cFwd        = WireDefault(false.B)

      for (j <- 0 until bufSize) {
        val olderIdx = (entries.frontIndex + j.U) % bufSize.U
        val olderEntry = entries.at(olderIdx)
        when(j.U < i.U && olderEntry.isValid && olderEntry.isStore) {
          when(olderEntry.addrReady && entry.addrReady && olderEntry.addrValue === entry.addrValue) {
            hasReliance := true.B
            when(olderEntry.dataReady && olderEntry.dataLen === entry.dataLen) {
              cFwd := true.B
              fData := olderEntry.dataValue
            }
          } .elsewhen(!olderEntry.addrReady) {
            hasReliance := true.B
          }
        }
      }
      
      canIssueLoad(i) := entry.addrReady && (!hasReliance || cFwd)
      canForward(i)   := cFwd
      forwardData(i)  := fData
    }
  }

  val issueIdxInQueue = PriorityEncoder(canIssueLoad)
  val issueIdx = (entries.frontIndex + issueIdxInQueue) % bufSize.U
  val doLoadIssue = canIssueLoad.asUInt.orR

  when(doLoadIssue && !io.flushInput.isFlush) {
    val target = entries.at(issueIdx)
    when(canForward(issueIdxInQueue)) {
      val updated = WireDefault(target)
      updated.dataValue := forwardData(issueIdxInQueue)
      updated.dataReady := true.B
      updated.isExecuted := true.B
      entries.writeAt(issueIdx, updated)
      
      io.cdbOutput.entry.isValid  := true.B
      io.cdbOutput.entry.robIndex := target.robIndex
      io.cdbOutput.entry.value    := forwardData(issueIdxInQueue)
    } .elsewhen(!loadSent) {
      // execute
      io.miuOutput.isLoadRequest := true.B
      io.miuOutput.addr          := target.addrValue
      io.miuOutput.dataLen       := target.dataLen
      loadSent    := true.B
      loadIdxReg  := issueIdx
    }
  }

  // MIU Load Reply
  when(io.miuInput.isLoadReply && loadSent) {
    val e = entries.at(loadIdxReg)
    val u = WireDefault(e)
    u.dataValue := io.miuInput.value
    u.dataReady := true.B
    u.isExecuted := true.B
    entries.writeAt(loadIdxReg, u)

    io.cdbOutput.entry.isValid  := true.B
    io.cdbOutput.entry.robIndex := e.robIndex
    io.cdbOutput.entry.value    := io.miuInput.value
    loadSent := false.B
  }

  // Store Execution
  val headEntry = entries.front
  when(!entries.empty && headEntry.isStore && headEntry.isExecuted && headEntry.isCommitted && !storeSent) {
    io.miuOutput.isStoreRequest := true.B
    io.miuOutput.addr           := headEntry.addrValue
    io.miuOutput.value          := headEntry.dataValue
    io.miuOutput.dataLen        := headEntry.dataLen
    storeSent := true.B
  }

  when(io.miuInput.isStoreReply && storeSent) {
    val u = WireDefault(entries.front)
    u.isFinished := true.B
    entries.writeAt(entries.frontIndex, u)
    storeSent := false.B
  }

  // Request for RoB Store Commit
  for (i <- 0 until bufSize) {
    val idx = (entries.frontIndex + i.U) % bufSize.U
    val e = entries.at(idx)
    when(i.U < entries.size && e.isStore && !e.isExecuted && e.addrReady && e.dataReady) {
      val u = WireDefault(e)
      u.isExecuted := true.B
      entries.writeAt(idx, u)
      io.robOutput.isValid  := true.B
      io.robOutput.robIndex := e.robIndex
    }
  }

  when(io.duInput.isValid && !entries.full) {
    val n = Wire(new Entry); n := 0.U.asTypeOf(new Entry)
    n.isValid := true.B; n.isLoad := io.duInput.isLoad; n.isStore := io.duInput.isStore
    n.dataLen := io.duInput.dataLen; n.robIndex := io.duInput.robIndex
    n.dataReady := io.duInput.dataReady; n.dataIndex := io.duInput.dataIndex; n.dataValue := io.duInput.dataValue
    entries.push(n)
  }

  when(io.flushInput.isFlush) {
    entries.clear()
    loadSent := false.B
    storeSent := false.B
  } .elsewhen(!entries.empty && entries.front.isFinished) {
    entries.pop()
  }
}