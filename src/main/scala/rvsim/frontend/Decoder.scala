package rvsim.frontend

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class Decoder extends Module {
  val io = IO(new Bundle {
    val ifInput = Flipped(new IFToDecoder)
    val duOutput = new DecoderToDU
  })

  val instReg = RegEnable(io.ifInput.req.bits.instr, 0.U, io.ifInput.req.fire)
  val pcReg   = RegEnable(io.ifInput.req.bits.pc, 0.U, io.ifInput.req.fire)
  val predReg = RegEnable(io.ifInput.req.bits.predPC, 0.U, io.ifInput.req.fire)
  val valReg  = RegInit(false.B)

  when(io.ifInput.req.fire) {
    valReg := true.B
  } .elsewhen(io.duOutput.decodedInstr.fire) {
    valReg := false.B
  }

  val instr = instReg
  val opcode = instr(6, 0)
  val rd     = instr(11, 7)
  val funct3 = instr(14, 12)
  val rs1    = instr(19, 15)
  val rs2    = instr(24, 20)
  val funct7 = instr(31, 25)

  val immI = Cat(Fill(20, instr(31)), instr(31, 20))
  val immS = Cat(Fill(20, instr(31)), instr(31, 25), instr(11, 7))
  val immB = Cat(Fill(19, instr(31)), instr(31), instr(7), instr(30, 25), instr(11, 8), 0.U(1.W))
  val immU = Cat(instr(31, 12), 0.U(12.W))
  val immJ = Cat(Fill(11, instr(31)), instr(31), instr(19, 12), instr(20), instr(30, 21), 0.U(1.W))

  val isALUImm  = opcode === "b0010011".U
  val isALUReg  = opcode === "b0110011".U
  val isLoad    = opcode === "b0000011".U
  val isStore   = opcode === "b0100011".U
  val isBranch  = opcode === "b1100011".U
  val isJal     = opcode === "b1101111".U
  val isJalr    = opcode === "b1100111".U
  val isLui     = opcode === "b0110111".U
  val isAuipc   = opcode === "b0010111".U

  val isM = isALUReg && (funct7 === "b0000001".U)
  
  val d = io.duOutput.decodedInstr.bits
  d.pc     := pcReg
  d.instr  := instReg
  d.opcode := opcode
  d.rd     := rd
  d.rs1    := rs1
  d.rs2    := rs2
  d.funct3 := funct3
  d.funct7 := funct7
  d.predPC := predReg

  d.imm := MuxCase(0.U, Seq(
    (isALUImm || isLoad || isJalr) -> immI,
    isStore                        -> immS,
    isBranch                       -> immB,
    (isLui || isAuipc)             -> immU,
    isJal                          -> immJ
  ))
  d.useImm := isALUImm || isLui || isAuipc || isStore || isLoad || isJalr

  d.isALU    := (isALUImm || (isALUReg && !isM) || isLui || isAuipc)
  d.isMul    := isM && (funct3(2) === 0.U)
  d.isDiv    := isM && (funct3(2) === 1.U)
  d.isBranch := isBranch
  d.isJump   := (isJal || isJalr)
  d.isLoad   := isLoad
  d.isStore  := isStore

  d.needsRs1 := (isALUReg || isALUImm || isLoad || isStore || isBranch || isJalr)
  d.needsRs2 := (isALUReg || isStore || isBranch)
  d.writesRd := (rd =/= 0.U) && (isALUReg || isALUImm || isLoad || isJal || isJalr || isLui || isAuipc)

  io.duOutput.decodedInstr.valid := valReg
  io.ifInput.req.ready           := !valReg || io.duOutput.decodedInstr.ready
  
  when(io.ifInput.req.valid) {
    printf("Decoder: decoded instr %x at pc %x\n", d.instr, d.pc)
  }
}