package rvsim.execution

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class ReservationStation extends Module {
  val io = IO(new Bundle {
    val duInput    = Flipped(new DUToRS)
    val flushInput = new FlushListener
    val cdbInput   = new CDBListener
    val aluOutput  = new RSToALU
  })

  // entries and correlated validness
  val rsEntries = RegInit(VecInit(Seq.fill(Config.RS_ENTRIES)(0.U.asTypeOf(new RSEntry))))
  val rsValids  = RegInit(VecInit(Seq.fill(Config.RS_ENTRIES)(false.B)))

  // listen CDB
  when(io.cdbInput.in.valid) {
    val cdb = io.cdbInput.in.bits
    for (i <- 0 until Config.RS_ENTRIES) {
      when(rsValids(i)) {
        // rs1
        when(!rsEntries(i).src1.ready && rsEntries(i).src1.robIdx === cdb.robIdx) {
          rsEntries(i).src1.data  := cdb.data
          rsEntries(i).src1.ready := true.B
        }
        // rs2
        when(!rsEntries(i).src2.ready && rsEntries(i).src2.robIdx === cdb.robIdx) {
          rsEntries(i).src2.data  := cdb.data
          rsEntries(i).src2.ready := true.B
        }
      }
    }
  }

  // find a free slot
  val freeIdx = PriorityEncoder(~rsValids.asUInt)
  val hasFree = !rsValids.asUInt.andR
  
  io.duInput.allocReq.ready := hasFree

  when(io.duInput.allocReq.fire) {
    rsEntries(freeIdx) := io.duInput.allocReq.bits
    rsValids(freeIdx)  := true.B
    printf(p"RS: DU input a new RSEntry: ${rsEntries(freeIdx)}\n")
  }

  // read CDB and issue to ALU
  val readyToFire = VecInit((0 until Config.RS_ENTRIES).map { i =>
    val entry = rsEntries(i)
    val cdb   = io.cdbInput.in
    
    val s1Ready = entry.src1.ready || (cdb.valid && cdb.bits.robIdx === entry.src1.robIdx)
    val s2Ready = entry.src2.ready || (cdb.valid && cdb.bits.robIdx === entry.src2.robIdx)
    
    rsValids(i) && s1Ready && s2Ready
  })

  val issueIdx = PriorityEncoder(readyToFire.asUInt)
  val hasReady = readyToFire.asUInt.orR

  io.aluOutput.entry.valid := hasReady
  
  val selectedEntry = rsEntries(issueIdx)
  val finalEntry = WireInit(selectedEntry)
  
  val cdb = io.cdbInput.in
  when(cdb.valid) {
    when(!selectedEntry.src1.ready && selectedEntry.src1.robIdx === cdb.bits.robIdx) {
      finalEntry.src1.data := cdb.bits.data
    }
    when(!selectedEntry.src2.ready && selectedEntry.src2.robIdx === cdb.bits.robIdx) {
      finalEntry.src2.data := cdb.bits.data
    }
  }
  
  io.aluOutput.entry.bits.opcode      := finalEntry.opcode
  io.aluOutput.entry.bits.funct3      := finalEntry.funct3
  io.aluOutput.entry.bits.funct7      := finalEntry.funct7
  io.aluOutput.entry.bits.imm         := finalEntry.imm
  io.aluOutput.entry.bits.useImm      := finalEntry.useImm
  io.aluOutput.entry.bits.destReg     := finalEntry.destReg
  io.aluOutput.entry.bits.archDestReg := finalEntry.archDestReg
  io.aluOutput.entry.bits.robIdx      := finalEntry.robIdx
  io.aluOutput.entry.bits.pc          := finalEntry.pc
  io.aluOutput.entry.bits.predictedNextPC := finalEntry.predictedNextPC
  io.aluOutput.entry.bits.isALU       := finalEntry.isALU
  io.aluOutput.entry.bits.isMul       := finalEntry.isMul
  io.aluOutput.entry.bits.isDiv       := finalEntry.isDiv
  io.aluOutput.entry.bits.isBranch    := finalEntry.isBranch
  io.aluOutput.entry.bits.isJump      := finalEntry.isJump
  io.aluOutput.entry.bits.instr       := finalEntry.instr
  io.aluOutput.entry.bits.src1        := finalEntry.src1.data
  io.aluOutput.entry.bits.src2        := finalEntry.src2.data

  when(io.aluOutput.entry.fire) {
    rsValids(issueIdx) := false.B
  }

  // flush
  when(io.flushInput.req.valid) {
    rsValids.foreach(_ := false.B)
  }

  io.flushInput.flushed := true.B 
}