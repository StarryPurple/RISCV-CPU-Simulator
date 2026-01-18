package rvsim2

import chisel3._
import chisel3.util._

class ArithLogicUnit extends Module {
  val io = IO(new Bundle {
    val rsInput    = Input(new RSToALU)
    val flushInput = Input(new FlushPipeline)
    val cdbOutput  = Output(new ALUToCDB)
    val rsOutput   = Output(new ALUToRS)
  })

  val isBusy      = RegInit(false.B)
  val robIndex    = RegInit(0.U(Config.ROBIdxWidth))
  val instrType   = RegInit(Instructions.Type.INVALID)
  val src1Value   = RegInit(0.U(Config.XLEN.W))
  val src2Value   = RegInit(0.U(Config.XLEN.W))
  val imm         = RegInit(0.S(Config.XLEN.W))
  val dstReg      = RegInit(0.U(Config.RFIdxWidth))
  val instrAddr   = RegInit(0.U(Config.XLEN.W))
  val isBranchBit = RegInit(false.B)
  val predPc      = RegInit(0.U(Config.XLEN.W))

  when(io.flushInput.isFlush) {
    isBusy    := false.B
    instrType := Instructions.Type.INVALID
  } .elsewhen(isBusy) {
    isBusy    := false.B
    instrType := Instructions.Type.INVALID
  } .otherwise {
    when(io.rsInput.isValid) {
      isBusy      := true.B
      robIndex    := io.rsInput.robIndex
      instrType   := io.rsInput.instrType
      src1Value   := io.rsInput.src1Value
      src2Value   := io.rsInput.src2Value
      imm         := io.rsInput.imm
      dstReg      := io.rsInput.dstReg
      instrAddr   := io.rsInput.instrAddr
      isBranchBit := io.rsInput.isBranch
      predPc      := io.rsInput.predPc
    }
  }

  val result       = WireDefault(0.U(Config.XLEN.W))
  val realBranchPc = WireDefault(0.U(Config.XLEN.W))
  val isBranchInst = WireDefault(false.B)
  val isLoadStore  = WireDefault(false.B)
  val shamt        = imm.asUInt(4, 0)
  val it           = Instructions.Type

  switch(instrType) {
    is(it.ADD)   { result := src1Value + src2Value }
    is(it.ADDI)  { result := src1Value + imm.asUInt }
    is(it.SUB)   { result := src1Value - src2Value }
    is(it.SLT)   { result := (src1Value.asSInt < src2Value.asSInt).asUInt }
    is(it.SLTU)  { result := (src1Value < src2Value).asUInt }
    is(it.XOR)   { result := src1Value ^ src2Value }
    is(it.XORI)  { result := src1Value ^ imm.asUInt }
    is(it.OR)    { result := src1Value | src2Value }
    is(it.ORI)   { result := src1Value | imm.asUInt }
    is(it.AND)   { result := src1Value & src2Value }
    is(it.ANDI)  { result := src1Value & imm.asUInt }
    is(it.SLL)   { result := src1Value << src2Value(4, 0) }
    is(it.SLLI)  { result := src1Value << shamt }
    is(it.SLTI)  { result := (src1Value.asSInt < imm).asUInt }
    is(it.SLTIU) { result := (src1Value < imm.asUInt).asUInt }
    is(it.SRL)   { result := src1Value >> src2Value(4, 0) }
    is(it.SRLI)  { result := src1Value >> shamt }
    is(it.SRA)   { result := (src1Value.asSInt >> src2Value(4, 0)).asUInt }
    is(it.SRAI)  { result := (src1Value.asSInt >> shamt).asUInt }
    is(it.LUI)   { result := (imm.asUInt << 12) }
    is(it.AUIPC) { result := instrAddr + (imm.asUInt << 12) }
    
    is(it.BEQ) {
      isBranchInst := true.B
      realBranchPc := Mux(src1Value === src2Value, instrAddr + imm.asUInt, instrAddr + 4.U)
    }
    is(it.BNE) {
      isBranchInst := true.B
      realBranchPc := Mux(src1Value =/= src2Value, instrAddr + imm.asUInt, instrAddr + 4.U)
    }
    is(it.BLT) {
      isBranchInst := true.B
      realBranchPc := Mux(src1Value.asSInt < src2Value.asSInt, instrAddr + imm.asUInt, instrAddr + 4.U)
    }
    is(it.BGE) {
      isBranchInst := true.B
      realBranchPc := Mux(src1Value.asSInt >= src2Value.asSInt, instrAddr + imm.asUInt, instrAddr + 4.U)
    }
    is(it.BLTU) {
      isBranchInst := true.B
      realBranchPc := Mux(src1Value < src2Value, instrAddr + imm.asUInt, instrAddr + 4.U)
    }
    is(it.BGEU) {
      isBranchInst := true.B
      realBranchPc := Mux(src1Value >= src2Value, instrAddr + imm.asUInt, instrAddr + 4.U)
    }
    is(it.JAL) {
      isBranchInst := true.B
      realBranchPc := instrAddr + imm.asUInt
      result       := instrAddr + 4.U
    }
    is(it.JALR) {
      isBranchInst := true.B
      realBranchPc := (src1Value + imm.asUInt) & ~1.U(Config.XLEN.W)
      result       := instrAddr + 4.U
    }
    is(it.LB, it.LH, it.LW, it.LBU, it.LHU, it.SB, it.SH, it.SW) {
      result       := src1Value + imm.asUInt
      isLoadStore  := true.B
    }
  }

  io.cdbOutput.entry.isValid     := isBusy
  io.cdbOutput.entry.robIndex    := robIndex
  io.cdbOutput.entry.realPc      := Mux(isBranchInst, realBranchPc, 0.U)
  io.cdbOutput.entry.value       := result
  io.cdbOutput.entry.isLoadStore := isLoadStore

  io.rsOutput.canAcceptInstr := !isBusy
}