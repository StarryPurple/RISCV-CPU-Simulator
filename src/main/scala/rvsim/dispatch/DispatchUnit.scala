package rvsim.dispatch

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class DispatchUnit extends Module {
  val io = IO(new Bundle {
    val decInput = Flipped(new DecoderToDU)
    val rfInput = Flipped(new RFToDU)
    val robInput = Flipped(new RoBToDU)
    val flushInput = new FlushListener
    val cdbInput = new CDBListener
    val rfOutput = new DUToRF
    val robOutput = new DUToRoB
    val rsOutput = new DUToRS
    val lsbOutput = new DUToLSB
  })

  // RAT / Free list
  val tagTable  = RegInit(VecInit(Seq.fill(32)(0.U(Config.ROB_IDX_WIDTH.W))))
  val busyTable = RegInit(VecInit(Seq.fill(32)(false.B)))

  val dec = io.decInput.decodedInstr.bits
  val robAlloc = io.robInput.allocResp

  // read from rf
  io.rfOutput.readReq(0).valid := io.decInput.decodedInstr.valid
  io.rfOutput.readReq(0).bits  := dec.rs1
  io.rfOutput.readReq(1).valid := io.decInput.decodedInstr.valid
  io.rfOutput.readReq(1).bits  := dec.rs2

  def resolveOperand(rsIdx: UInt, rfData: UInt) = {
    val op = Wire(new OperandInfo)
    val isBusy = busyTable(rsIdx) && (rsIdx =/= 0.U)
    val tag    = tagTable(rsIdx)
    
    // cdb listen
    val cdbHit = io.cdbInput.in.valid && io.cdbInput.in.bits.robIdx === tag

    op.ready  := !isBusy || cdbHit
    op.data   := Mux(cdbHit, io.cdbInput.in.bits.data, rfData)
    op.robIdx := tag
    op
  }

  val op1 = resolveOperand(dec.rs1, io.rfInput.readRsp(0).data)
  val op2 = resolveOperand(dec.rs2, io.rfInput.readRsp(1).data)

  val isLS = dec.isLoad || dec.isStore
  
  // 1. Decoder gives a valid instruction
  // 2. RoB allows new entry (allocResp.valid)
  // 3. RS/LSB (the correlated one) allows new entry
  val canDispatch = io.decInput.decodedInstr.valid && robAlloc.valid && 
                    Mux(isLS, io.lsbOutput.allocReq.ready, io.rsOutput.allocReq.ready)

  // backup
  io.decInput.decodedInstr.ready := canDispatch
  io.robOutput.allocReq.valid    := canDispatch
  io.robOutput.allocReq.bits     := dec // the datas

  // try to fire
  io.rsOutput.allocReq.valid  := canDispatch && !isLS
  io.lsbOutput.allocReq.valid := canDispatch && isLS

  // RS fire
  val rsEntry = io.rsOutput.allocReq.bits
  rsEntry := 0.U.asTypeOf(new RSEntry)
  when(canDispatch && !isLS) {
    rsEntry.opcode      := dec.opcode
    rsEntry.funct3      := dec.funct3
    rsEntry.funct7      := dec.funct7
    rsEntry.imm         := dec.imm
    rsEntry.src1        := op1
    rsEntry.src2        := op2
    rsEntry.archDestReg := dec.rd
    rsEntry.robIdx      := robAlloc.bits.robIdx
    rsEntry.pc          := dec.pc
    rsEntry.instr       := dec.instr
    rsEntry.useImm      := dec.useImm                            // use imm / rs2?
    rsEntry.predictedNextPC := dec.predPC
    rsEntry.isALU       := dec.isALU
    rsEntry.isMul       := dec.isMul
    rsEntry.isDiv       := dec.isDiv
    rsEntry.isBranch    := dec.isBranch
    rsEntry.isJump      := dec.isJump
  }

  // LSB fire
  val lsbEntry = io.lsbOutput.allocReq.bits
  lsbEntry := 0.U.asTypeOf(new LSBEntry)
  when(canDispatch && isLS) {
    lsbEntry.isLoad      := dec.isLoad
    lsbEntry.isStore     := dec.isStore
    lsbEntry.addr.base   := op1.data
    lsbEntry.addr.baseReady := op1.ready
    lsbEntry.addr.baseRobIdx := op1.robIdx
    lsbEntry.addr.offset := dec.imm.asSInt
    lsbEntry.storeData.value := op2.data
    lsbEntry.storeData.ready := op2.ready
    lsbEntry.archDestReg := dec.rd
    lsbEntry.robIdx      := robAlloc.bits.robIdx
    lsbEntry.pc          := dec.pc
    lsbEntry.instr       := dec.instr
  }

  // RAT 
  when(canDispatch && dec.rd =/= 0.U) {
    busyTable(dec.rd) := true.B
    tagTable(dec.rd)  := robAlloc.bits.robIdx
  }

  // flush
  when(io.flushInput.req.valid) {
    busyTable.foreach(_ := false.B)
  } .otherwise {
    
    // commit 
    val commit = io.robInput.commitMsg
    val regWrite = commit.bits.regWrite
    
    when(commit.valid && regWrite.valid && regWrite.bits.rd =/= 0.U) {
      // WAW problem: overwrite only at latest tag (robIdx) commited matches
      when(tagTable(regWrite.bits.rd) === regWrite.bits.robIdx) {
        busyTable(regWrite.bits.rd) := false.B
      }
    }
  }
}