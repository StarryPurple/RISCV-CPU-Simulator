package rvsim3

import chisel3._
import chisel3.util._

object InstrType extends ChiselEnum {
  val INVALID = Value
  val LUI, AUIPC, JAL, JALR = Value
  val BEQ, BNE, BLT, BGE, BLTU, BGEU = Value
  val LB, LH, LW, LBU, LHU = Value
  val SB, SH, SW = Value
  val ADDI, SLTI, SLTIU, XORI, ORI, ANDI, SLLI, SRLI, SRAI = Value
  val ADD, SUB, SLL, SLT, SLTU, XOR, SRL, SRA, OR, AND = Value
}

class DecodedInst extends Bundle {
  val inst  = UInt(32.W)
  val pc    = UInt(32.W)
  val itype = InstrType()

  def rd     = inst(11, 7)
  def rs1    = inst(19, 15)
  def rs2    = inst(24, 20)
  def funct3 = inst(14, 12)
  def funct7 = inst(31, 25)
  def opcode = inst(6, 0)

  def imm: UInt = {
    val iImm = Cat(Fill(20, inst(31)), inst(31, 20))
    val sImm = Cat(Fill(20, inst(31)), inst(31, 25), inst(11, 7))
    val bImm = Cat(Fill(19, inst(31)), inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W))
    val uImm = Cat(inst(31, 12), 0.U(12.W))
    val jImm = Cat(Fill(11, inst(31)), inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W))
    val shamt = inst(24, 20)

    MuxLookup(itype.asUInt, iImm)(Seq(
      InstrType.LUI.asUInt   -> uImm,
      InstrType.AUIPC.asUInt -> uImm,
      InstrType.JAL.asUInt   -> jImm,
      InstrType.BEQ.asUInt   -> bImm, InstrType.BNE.asUInt  -> bImm,
      InstrType.BLT.asUInt   -> bImm, InstrType.BGE.asUInt  -> bImm,
      InstrType.BLTU.asUInt  -> bImm, InstrType.BGEU.asUInt -> bImm,
      InstrType.SB.asUInt    -> sImm, InstrType.SH.asUInt   -> sImm, InstrType.SW.asUInt -> sImm,
      InstrType.SLLI.asUInt  -> shamt, InstrType.SRLI.asUInt -> shamt, InstrType.SRAI.asUInt -> shamt
    ))
  }

  def hasSrc1: Bool = {
    !VecInit(InstrType.LUI, InstrType.AUIPC, InstrType.JAL, InstrType.INVALID)
      .exists(_ === itype)
  }

  def hasSrc2: Bool = {
    VecInit(
      InstrType.ADD, InstrType.SUB, InstrType.SLL, InstrType.SLT, InstrType.SLTU,
      InstrType.XOR, InstrType.SRL, InstrType.SRA, InstrType.OR, InstrType.AND,
      InstrType.BEQ, InstrType.BNE, InstrType.BLT, InstrType.BGE, InstrType.BLTU, InstrType.BGEU,
      InstrType.SB,  InstrType.SH,  InstrType.SW
    ).exists(_ === itype)
  }

  def writeRf: Bool = {
    val noWrite = VecInit(
      InstrType.BEQ, InstrType.BNE, InstrType.BLT, InstrType.BGE, InstrType.BLTU, InstrType.BGEU,
      InstrType.SB,  InstrType.SH,  InstrType.SW,  InstrType.INVALID
    ).exists(_ === itype)
    !noWrite && (rd =/= 0.U)
  }

  def isLoad  = itype >= InstrType.LB  && itype <= InstrType.LHU
  def isStore = itype >= InstrType.SB  && itype <= InstrType.SW
  def isBr    = itype >= InstrType.BEQ && itype <= InstrType.BGEU
  def isJal   = itype === InstrType.JAL
  def isJalr  = itype === InstrType.JALR

  def memDataLen: UInt = {
    MuxLookup(itype.asUInt, 0.U)(Seq(
      InstrType.LB.asUInt  -> 1.U, InstrType.LBU.asUInt -> 1.U, InstrType.SB.asUInt -> 1.U,
      InstrType.LH.asUInt  -> 2.U, InstrType.LHU.asUInt -> 2.U, InstrType.SH.asUInt -> 2.U,
      InstrType.LW.asUInt  -> 4.U, InstrType.SW.asUInt  -> 4.U
    ))
  }
}

object DecodedInst {
  def apply(raw_instr: UInt, pc: UInt): DecodedInst = {
    val d = Wire(new DecodedInst)
    d.inst := raw_instr
    d.pc   := pc
    
    val f3 = raw_instr(14, 12)
    val f7 = raw_instr(31, 25)
    val op = raw_instr(6, 0)

    d.itype := MuxLookup(op, InstrType.INVALID)(Seq(
      "b0110111".U -> InstrType.LUI,
      "b0010111".U -> InstrType.AUIPC,
      "b1101111".U -> InstrType.JAL,
      "b1100111".U -> InstrType.JALR,
      "b1100011".U -> MuxLookup(f3, InstrType.INVALID)(Seq(
        "b000".U -> InstrType.BEQ,  "b001".U -> InstrType.BNE,
        "b100".U -> InstrType.BLT,  "b101".U -> InstrType.BGE,
        "b110".U -> InstrType.BLTU, "b111".U -> InstrType.BGEU
      )),
      "b0000011".U -> MuxLookup(f3, InstrType.INVALID)(Seq(
        "b000".U -> InstrType.LB,  "b001".U -> InstrType.LH,
        "b010".U -> InstrType.LW,  "b100".U -> InstrType.LBU,
        "b101".U -> InstrType.LHU
      )),
      "b0100011".U -> MuxLookup(f3, InstrType.INVALID)(Seq(
        "b000".U -> InstrType.SB, "b001".U -> InstrType.SH, "b010".U -> InstrType.SW
      )),
      "b0010011".U -> MuxLookup(f3, InstrType.INVALID)(Seq(
        "b000".U -> InstrType.ADDI,  "b010".U -> InstrType.SLTI,
        "b011".U -> InstrType.SLTIU, "b100".U -> InstrType.XORI,
        "b110".U -> InstrType.ORI,   "b111".U -> InstrType.ANDI,
        "b001".U -> InstrType.SLLI,
        "b101".U -> Mux(f7(5), InstrType.SRAI, InstrType.SRLI)
      )),
      "b0110011".U -> MuxLookup(f3, InstrType.INVALID)(Seq(
        "b000".U -> Mux(f7(5), InstrType.SUB, InstrType.ADD),
        "b001".U -> InstrType.SLL,  "b010".U -> InstrType.SLT,
        "b011".U -> InstrType.SLTU, "b100".U -> InstrType.XOR,
        "b101".U -> Mux(f7(5), InstrType.SRA, InstrType.SRL),
        "b110".U -> InstrType.OR,   "b111".U -> InstrType.AND
      ))
    ))
    d
  }
}