package rvsim2

import chisel3._
import chisel3.util._

class ReservationStation(val stnSize: Int = 8) extends Module {
  val io = IO(new Bundle {
    val duInput     = Input(new DUToRS)
    val cdbInput    = Input(new CDBOut)
    val flushInput  = Input(new FlushPipeline)
    val aluInput    = Input(new ALUToRS)
    val aluOutput   = Output(new RSToALU)
    val duOutput    = Output(new RSToDU)
  })

  class Entry extends Bundle {
    val isValid    = Bool()
    val robIndex   = UInt(Config.ROBIdxWidth)
    val instrType  = Instructions.Type()
    val instrAddr  = UInt(Config.XLEN.W)
    val src1Ready  = Bool()
    val src1Value  = UInt(Config.XLEN.W)
    val src1Index  = UInt(Config.ROBIdxWidth)
    val src2Ready  = Bool()
    val src2Value  = UInt(Config.XLEN.W)
    val src2Index  = UInt(Config.ROBIdxWidth)
    val imm        = SInt(Config.XLEN.W)
    val dstReg     = UInt(Config.RFIdxWidth)
    val isBranch   = Bool()
    val predPc     = UInt(Config.XLEN.W)
  }

  val entries = RegInit(VecInit(Seq.fill(stnSize)(0.U.asTypeOf(new Entry))))
  val size    = RegInit(0.U(log2Ceil(stnSize + 1).W))

  io.aluOutput := 0.U.asTypeOf(new RSToALU)
  io.duOutput  := 0.U.asTypeOf(new RSToDU)

  // 1. CDB Monitoring (Data Snooping)
  for (entry <- entries) {
    when(entry.isValid) {
      val lsbMatch1 = io.cdbInput.lsbEntry.isValid && !entry.src1Ready && entry.src1Index === io.cdbInput.lsbEntry.robIndex
      val lsbMatch2 = io.cdbInput.lsbEntry.isValid && !entry.src2Ready && entry.src2Index === io.cdbInput.lsbEntry.robIndex
      val aluMatch1 = io.cdbInput.aluEntry.isValid && !entry.src1Ready && entry.src1Index === io.cdbInput.aluEntry.robIndex
      val aluMatch2 = io.cdbInput.aluEntry.isValid && !entry.src2Ready && entry.src2Index === io.cdbInput.aluEntry.robIndex

      when(lsbMatch1) { entry.src1Value := io.cdbInput.lsbEntry.value; entry.src1Ready := true.B }
      .elsewhen(aluMatch1) { entry.src1Value := io.cdbInput.aluEntry.value; entry.src1Ready := true.B }

      when(lsbMatch2) { entry.src2Value := io.cdbInput.lsbEntry.value; entry.src2Ready := true.B }
      .elsewhen(aluMatch2) { entry.src2Value := io.cdbInput.aluEntry.value; entry.src2Ready := true.B }
    }
  }

  // 2. Issue Logic
  val readyBits = VecInit(entries.map(e => e.isValid && e.src1Ready && e.src2Ready)).asUInt
  val hasReady  = readyBits.orR
  
  val issueIdx = PriorityEncoder(readyBits)
  val canIssue = hasReady && io.aluInput.canAcceptInstr

  when(canIssue && !io.flushInput.isFlush) {
    val issued = entries(issueIdx)
    io.aluOutput.isValid   := true.B
    io.aluOutput.robIndex  := issued.robIndex
    io.aluOutput.instrType := issued.instrType
    io.aluOutput.src1Value := issued.src1Value
    io.aluOutput.src2Value := issued.src2Value
    io.aluOutput.imm       := issued.imm
    io.aluOutput.dstReg    := issued.dstReg
    io.aluOutput.instrAddr := issued.instrAddr
    io.aluOutput.isBranch  := issued.isBranch
    io.aluOutput.predPc    := issued.predPc
    
    entries(issueIdx).isValid := false.B
  }

  // 3. Dispatch Logic
  val freeIdx = PriorityEncoder(entries.map(!_.isValid))
  val canAccept = size < stnSize.U

  when(io.flushInput.isFlush) {
    for (entry <- entries) entry.isValid := false.B
    size := 0.U
  } .otherwise {
    when(io.duInput.isValid && canAccept) {
      val newEntry = entries(freeIdx)
      newEntry.isValid   := true.B
      newEntry.robIndex  := io.duInput.robIndex
      newEntry.instrType := io.duInput.instrType
      newEntry.instrAddr := io.duInput.instrAddr
      newEntry.src1Ready := io.duInput.src1Ready
      newEntry.src1Value := io.duInput.src1Value
      newEntry.src1Index := io.duInput.src1Index
      newEntry.src2Ready := io.duInput.src2Ready
      newEntry.src2Value := io.duInput.src2Value
      newEntry.src2Index := io.duInput.src2Index
      newEntry.imm       := io.duInput.imm
      newEntry.dstReg    := io.duInput.dstReg
      newEntry.isBranch  := io.duInput.isBranch
      newEntry.predPc    := io.duInput.predPc
    }
    
    val doIssue    = canIssue
    val doDispatch = io.duInput.isValid && canAccept
    size := size + doDispatch.asUInt - doIssue.asUInt
  }

  io.duOutput.canAcceptInstr := size < stnSize.U
}