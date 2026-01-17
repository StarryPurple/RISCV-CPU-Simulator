package rvsim.commit

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class ROBEntry extends Bundle {
  val valid     = Bool()
  val ready     = Bool()
  val pc        = UInt(Config.XLEN.W)
  val rd        = UInt(5.W)
  val value     = UInt(Config.XLEN.W) // alu calc res / br real pc
  val isLS      = Bool()
  val isBranch  = Bool()
  val predPC    = UInt(Config.XLEN.W)
  val instr     = UInt(Config.XLEN.W)
}

class ReorderBuffer extends Module {
  val io = IO(new Bundle {
    val duInput     = Flipped(new DUToRoB)
    val duOutput    = new RoBToDU
    val rfOutput    = new RoBToRF
    val lsbOutput   = new RoBToLSB 
    val predOutput  = new RoBToPred
    val flushOutput = new FlushAnnouncer
    val cdbInput    = new CDBListener
    val isTerminate = Output(Bool())
  })

  val robEntries = RegInit(VecInit(Seq.fill(Config.ROB_ENTRIES)(0.U.asTypeOf(new ROBEntry))))
  val head  = RegInit(0.U(Config.ROB_IDX_WIDTH.W))
  val tail  = RegInit(0.U(Config.ROB_IDX_WIDTH.W))
  val count = RegInit(0.U(log2Ceil(Config.ROB_ENTRIES + 1).W))

  val full  = count === Config.ROB_ENTRIES.U
  val empty = count === 0.U
  val isFlushing = RegInit(false.B)

  val canAllocate = !full && !isFlushing
  io.duInput.allocReq.ready := canAllocate
  io.duOutput.allocResp.valid := canAllocate
  io.duOutput.allocResp.bits.robIdx := tail

  when(io.duInput.allocReq.fire) {
    val newEntry = robEntries(tail)
    newEntry.valid    := true.B
    newEntry.ready    := false.B
    newEntry.pc       := io.duInput.allocReq.bits.pc
    newEntry.rd       := io.duInput.allocReq.bits.rd
    newEntry.instr    := io.duInput.allocReq.bits.instr
    newEntry.predPC   := io.duInput.allocReq.bits.predPC
    
    val op = io.duInput.allocReq.bits.opcode
    newEntry.isLS     := (op === "b0000011".U || op === "b0100011".U)
    newEntry.isBranch := (op === "b1100011".U || op === "b1101111".U || op === "b1100111".U)

    tail := tail + 1.U
    count := count + 1.U
  }

  when(io.cdbInput.in.valid) {
    val cdb = io.cdbInput.in.bits
    when(robEntries(cdb.robIdx).valid) {
      robEntries(cdb.robIdx).ready := true.B
      robEntries(cdb.robIdx).value := cdb.data
    }
  }

  val commitEntry = robEntries(head)
  val canCommit = commitEntry.valid && commitEntry.ready && !empty && !isFlushing

  // default
  io.rfOutput.regWrite.valid      := false.B
  io.lsbOutput.storeCommit.valid  := false.B
  io.predOutput.info.valid        := false.B
  io.flushOutput.req.valid        := false.B
  io.duOutput.commitMsg.valid     := false.B
  io.isTerminate                  := false.B

  when(canCommit) {
    val realTargetPC = commitEntry.value 
    val isMispredicted = commitEntry.isBranch && (realTargetPC =/= commitEntry.predPC)

    when(isMispredicted) {
      io.flushOutput.req.valid := true.B
      io.flushOutput.req.bits.targetPC := realTargetPC
      io.flushOutput.req.bits.flushFromRoBIdx.valid := true.B
      io.flushOutput.req.bits.flushFromRoBIdx.bits  := head
      isFlushing := true.B
    } .otherwise {
      val isStore = commitEntry.isLS && (commitEntry.instr(5) === 1.B) 
      val isLoad  = commitEntry.isLS && (commitEntry.instr(5) === 0.B)
      
      val shouldWriteRF = (commitEntry.rd =/= 0.U) && (!commitEntry.isLS || isLoad) && !commitEntry.isBranch

      when(shouldWriteRF) {
        io.rfOutput.regWrite.valid := true.B
        io.rfOutput.regWrite.bits.rd     := commitEntry.rd
        io.rfOutput.regWrite.bits.data   := commitEntry.value
        io.rfOutput.regWrite.bits.robIdx := head
        io.rfOutput.regWrite.bits.pc     := commitEntry.pc

        io.duOutput.commitMsg.valid := true.B
        io.duOutput.commitMsg.bits.regWrite.valid := true.B
        io.duOutput.commitMsg.bits.regWrite.bits.rd := commitEntry.rd
        io.duOutput.commitMsg.bits.regWrite.bits.robIdx := head
      }

      when(isStore) {
        io.lsbOutput.storeCommit.valid := true.B
        io.lsbOutput.storeCommit.bits.robIdx := head
      }

      when(commitEntry.isBranch) {
        io.predOutput.info.valid := true.B
        io.predOutput.info.bits.pc := commitEntry.pc
        io.predOutput.info.bits.target := realTargetPC
        io.predOutput.info.bits.taken := (realTargetPC =/= (commitEntry.pc + 4.U))
      }

      when(commitEntry.instr === Config.TERMINATE_INSTR.U) {
        io.isTerminate := true.B
      }

      robEntries(head).valid := false.B
      head := head + 1.U
      count := count - 1.U
    }
  }

  when(isFlushing && io.flushOutput.flushed) {
    robEntries.foreach(_.valid := false.B)
    head := 0.U
    tail := 0.U
    count := 0.U
    isFlushing := false.B
  }
}