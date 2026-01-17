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
    val predOutput = new RoBToPred
    val rfOutput = new RoBToRF
    val duOutput = new RoBToDU
    val lsbOutput = new RoBToLSB
    val flushOutput = new FlushAnnouncer
    val duInput = Flipped(new DUToRoB)
    val cdbInput = new CDBListener
    val isTerminate = Bool()
  })
  io.isTerminate := false.B

  val robEntries = RegInit(VecInit(Seq.fill(Config.ROB_ENTRIES)(0.U.asTypeOf(new ROBEntry))))
  val head = RegInit(0.U(Config.ROB_IDX_WIDTH.W))
  val tail = RegInit(0.U(Config.ROB_IDX_WIDTH.W))
  val full = RegInit(false.B)
  val empty = (head === tail) && !full
  val isFlushing = RegInit(false.B)


  val canAllocate = !full && !isFlushing
  io.duInput.allocReq.ready := canAllocate
  io.duOutput.allocResp.valid := canAllocate
  io.duOutput.allocResp.bits.robIdx := tail

  when(io.duInput.allocReq.fire) {
    robEntries(tail).valid    := true.B
    robEntries(tail).ready    := false.B
    robEntries(tail).pc       := io.duInput.allocReq.bits.pc
    robEntries(tail).rd       := io.duInput.allocReq.bits.rd
    
    val op = io.duInput.allocReq.bits.opcode
    robEntries(tail).isLS     := (op === "b0000011".U || op === "b0100011".U)
    robEntries(tail).isBranch := (op === "b1100011".U || op === "b1101111".U || op === "b1100111".U)

    robEntries(tail).predPC   := io.duInput.allocReq.bits.predPC
    robEntries(tail).instr    := io.duInput.allocReq.bits.instr

    tail := tail + 1.U
    when((tail + 1.U) === head) { full := true.B }
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

  io.rfOutput.regWrite.valid := false.B
  io.lsbOutput.storeCommit.valid := false.B
  io.predOutput.info.valid := false.B
  io.flushOutput.req.valid := false.B

  when(canCommit) {
    val realPC = commitEntry.value 
    val isMispredicted = commitEntry.isBranch && (realPC =/= (commitEntry.predPC))

    when(isMispredicted) {
      io.flushOutput.req.valid := true.B
      io.flushOutput.req.bits.targetPC := realPC
      io.flushOutput.req.bits.flushFromRoBIdx.valid := true.B
      io.flushOutput.req.bits.flushFromRoBIdx.bits  := head
      isFlushing := true.B
    } .otherwise {

      // RF
      when(commitEntry.rd =/= 0.U && !commitEntry.isLS && !commitEntry.isBranch) {
        io.rfOutput.regWrite.valid := true.B
        io.rfOutput.regWrite.bits.rd     := commitEntry.rd
        io.rfOutput.regWrite.bits.data   := commitEntry.value
        io.rfOutput.regWrite.bits.robIdx := head
        io.rfOutput.regWrite.bits.pc     := commitEntry.pc
      }

      // Pred
      when(commitEntry.isBranch) {
        io.predOutput.info.valid := true.B
        io.predOutput.info.bits.pc := commitEntry.pc
        io.predOutput.info.bits.target := realPC
        io.predOutput.info.bits.taken := (realPC =/= (commitEntry.pc + 4.U))
      }

      // LSB store commit
      when(commitEntry.isLS) {
        io.lsbOutput.storeCommit.valid := true.B
        io.lsbOutput.storeCommit.bits.robIdx := head
      }

      when(commitEntry.instr === Config.TERMINATE_INSTR.U) {
        io.isTerminate := true.B
      }

      robEntries(head).valid := false.B
      head := head + 1.U
      full := false.B
    }
  }

  // flush
  when(isFlushing && io.flushOutput.flushed) {
    robEntries.foreach(_.valid := false.B)
    head := 0.U; tail := 0.U; full := false.B; isFlushing := false.B
  }
}