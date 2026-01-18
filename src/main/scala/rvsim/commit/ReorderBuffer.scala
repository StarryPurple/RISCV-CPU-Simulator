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
  val value     = UInt(Config.XLEN.W)
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

  val isFlushing = RegInit(false.B)
  val terminateReg = RegInit(false.B)

  val full  = count === Config.ROB_ENTRIES.U
  val empty = count === 0.U

  val canAllocate = !full && !isFlushing
  io.duInput.allocReq.ready := canAllocate
  io.duOutput.allocResp.valid := canAllocate
  io.duOutput.allocResp.bits.robIdx := tail

  when(io.duInput.allocReq.fire) {
    val req = io.duInput.allocReq.bits
    val op = req.opcode
    
    robEntries(tail).valid    := true.B
    robEntries(tail).ready    := req.immediateDataValid
    robEntries(tail).value    := req.immediateData
    robEntries(tail).pc       := req.pc
    robEntries(tail).rd       := req.rd
    robEntries(tail).instr    := req.instr
    robEntries(tail).predPC   := req.predPC
    robEntries(tail).isLS     := (op === 0x03.U || op === 0x23.U)
    robEntries(tail).isBranch := (op === 0x63.U || op === 0x6F.U || op === 0x67.U)

    tail := tail + 1.U
    count := count + 1.U
  }

  when(io.cdbInput.in.valid) {
    val cdb = io.cdbInput.in.bits
    when(robEntries(cdb.robIdx).valid && !robEntries(cdb.robIdx).ready) {
      robEntries(cdb.robIdx).ready := true.B
      robEntries(cdb.robIdx).value := cdb.data
    }
  }

  val commitEntry = robEntries(head)
  val canCommit = commitEntry.valid && commitEntry.ready && !empty && !isFlushing

  io.rfOutput.regWrite.valid      := false.B
  io.rfOutput.regWrite.bits       := DontCare
  io.lsbOutput.storeCommit.valid  := false.B
  io.lsbOutput.storeCommit.bits   := DontCare
  io.predOutput.info.valid        := false.B
  io.predOutput.info.bits         := DontCare
  io.flushOutput.req.valid        := false.B
  io.flushOutput.req.bits         := DontCare
  io.duOutput.commitMsg.valid     := false.B
  io.duOutput.commitMsg.bits      := DontCare
  
  io.isTerminate := terminateReg && !isFlushing

  when(canCommit) {
    val realTargetPC = commitEntry.value 
    val isMispredicted = commitEntry.isBranch && (realTargetPC =/= commitEntry.predPC)

    when(isMispredicted) {
      io.flushOutput.req.valid := true.B
      io.flushOutput.req.bits.targetPC := realTargetPC
      io.flushOutput.req.bits.flushFromRoBIdx.valid := true.B
      io.flushOutput.req.bits.flushFromRoBIdx.bits  := head
      isFlushing := true.B
      terminateReg := false.B
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
        terminateReg := true.B
      }

      robEntries(head).valid := false.B
      head := head + 1.U
      count := count - 1.U
    }
  }

  when(isFlushing) {
    terminateReg := false.B
    when(io.flushOutput.flushed) {
      robEntries.foreach(_.valid := false.B)
      head := 0.U
      tail := 0.U
      count := 0.U
      isFlushing := false.B
    }
  }
}