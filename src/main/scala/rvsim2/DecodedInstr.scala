package rvsim2
import chisel3._
import chisel3.util._

object Instructions {
  object Type extends ChiselEnum {
    val INVALID = Value
    val LUI, AUIPC, JAL, JALR = Value
    val BEQ, BNE, BLT, BGE, BLTU, BGEU = Value
    val LB, LH, LW, LBU, LHU = Value
    val SB, SH, SW = Value
    val ADDI, SLTI, SLTIU, XORI, ORI, ANDI, SLLI, SRLI, SRAI = Value
    val ADD, SUB, SLL, SLT, SLTU, XOR, SRL, SRA, OR, AND = Value
  }
}

class DecodedInstr extends Bundle {
  val rawInstr = UInt(32.W)

  def opcode = rawInstr(6, 0)
  def funct3 = rawInstr(14, 12)
  def funct7 = rawInstr(31, 25)
  def rs1    = rawInstr(19, 15)
  def rs2    = rawInstr(24, 20)
  def rd     = rawInstr(11, 7)
  def shamt  = rawInstr(24, 20)

  def instrType: Instructions.Type.Type = {
    val it = Instructions.Type
    val res = WireDefault(it.INVALID)
    
    switch(opcode) {
      is("b0110111".U) { res := it.LUI }
      is("b0010111".U) { res := it.AUIPC }
      is("b1101111".U) { res := it.JAL }
      is("b1100111".U) { res := it.JALR }
      is("b1100011".U) { // Branch
        res := MuxLookup(funct3, it.INVALID)(Seq(
          "b000".U -> it.BEQ, "b001".U -> it.BNE, "b100".U -> it.BLT,
          "b101".U -> it.BGE, "b110".U -> it.BLTU, "b111".U -> it.BGEU
        ))
      }
      is("b0000011".U) { // Load
        res := MuxLookup(funct3, it.INVALID)(Seq(
          "b000".U -> it.LB, "b001".U -> it.LH, "b010".U -> it.LW,
          "b100".U -> it.LBU, "b101".U -> it.LHU
        ))
      }
      is("b0100011".U) { // Store
        res := MuxLookup(funct3, it.INVALID)(Seq(
          "b000".U -> it.SB, "b001".U -> it.SH, "b010".U -> it.SW
        ))
      }
      is("b0010011".U) { // I-Type
        res := MuxLookup(funct3, it.INVALID)(Seq(
          "b000".U -> it.ADDI, "b010".U -> it.SLTI, "b011".U -> it.SLTIU,
          "b100".U -> it.XORI, "b110".U -> it.ORI,  "b111".U -> it.ANDI,
          "b001".U -> it.SLLI,
          "b101".U -> Mux(funct7(5), it.SRAI, it.SRLI)
        ))
      }
      is("b0110011".U) { // R-Type
        res := MuxLookup(Cat(funct7, funct3), it.INVALID)(Seq(
          "b0000000_000".U -> it.ADD,  "b0100000_000".U -> it.SUB,
          "b0000000_001".U -> it.SLL,  "b0000000_010".U -> it.SLT,
          "b0000000_011".U -> it.SLTU, "b0000000_100".U -> it.XOR,
          "b0000000_101".U -> it.SRL,  "b0100000_101".U -> it.SRA,
          "b0000000_110".U -> it.OR,   "b0000000_111".U -> it.AND
        ))
      }
    }
    res
  }

  def imm: SInt = {
    val t = instrType
    val it = Instructions.Type
    
    val immI = rawInstr(31, 20).asSInt
    val immS = Cat(rawInstr(31, 25), rawInstr(11, 7)).asSInt
    val immB = Cat(rawInstr(31), rawInstr(7), rawInstr(30, 25), rawInstr(11, 8), 0.U(1.W)).asSInt
    val immU = Cat(rawInstr(31, 12), 0.U(12.W)).asSInt
    val immJ = Cat(rawInstr(31), rawInstr(19, 12), rawInstr(20), rawInstr(30, 21), 0.U(1.W)).asSInt

    val res = WireDefault(0.S(32.W))
    when(t === it.LUI || t === it.AUIPC) { res := immU }
    .elsewhen(t === it.JAL) { res := immJ }
    .elsewhen(t === it.JALR || t.isOneOf(it.LB, it.LH, it.LW, it.LBU, it.LHU, 
                                        it.ADDI, it.SLTI, it.SLTIU, it.XORI, it.ORI, it.ANDI)) {
      res := immI
    }
    .elsewhen(t.isOneOf(it.BEQ, it.BNE, it.BLT, it.BGE, it.BLTU, it.BGEU)) { res := immB }
    .elsewhen(t.isOneOf(it.SB, it.SH, it.SW)) { res := immS }
    .elsewhen(t.isOneOf(it.SLLI, it.SRLI, it.SRAI)) { res := shamt.zext.asSInt }
    
    res
  }

  def hasSrc1: Bool = {
    val it = Instructions.Type
    !instrType.isOneOf(it.LUI, it.AUIPC, it.JAL, it.INVALID)
  }

  def hasSrc2: Bool = {
    val it = Instructions.Type
    instrType.isOneOf(
      it.ADD, it.SUB, it.SLL, it.SLT, it.SLTU, it.XOR, it.SRL, it.SRA, it.OR, it.AND,
      it.BEQ, it.BNE, it.BLT, it.BGE, it.BLTU, it.BGEU,
      it.SB, it.SH, it.SW
    )
  }

  def writeRf: Bool = {
    val it = Instructions.Type
    !instrType.isOneOf(it.BEQ, it.BNE, it.BLT, it.BGE, it.BLTU, it.BGEU, it.SB, it.SH, it.SW, it.INVALID)
  }

  def isLoad: Bool  = instrType.isOneOf(Instructions.Type.LB, Instructions.Type.LH, Instructions.Type.LW, Instructions.Type.LBU, Instructions.Type.LHU)
  def isStore: Bool = instrType.isOneOf(Instructions.Type.SB, Instructions.Type.SH, Instructions.Type.SW)
  def isBr: Bool    = instrType.isOneOf(Instructions.Type.BEQ, Instructions.Type.BNE, Instructions.Type.BLT, Instructions.Type.BGE, Instructions.Type.BLTU, Instructions.Type.BGEU)
  def isJal: Bool   = instrType === Instructions.Type.JAL
  def isJalr: Bool  = instrType === Instructions.Type.JALR

  def memDataLen: UInt = {
    val it = Instructions.Type
    MuxLookup(instrType, 0.U)(Seq(
      it.LB  -> 1.U, it.LBU -> 1.U, it.SB -> 1.U,
      it.LH  -> 2.U, it.LHU -> 2.U, it.SH -> 2.U,
      it.LW  -> 4.U, it.SW  -> 4.U
    ))
  }
}

object DecodedInstr {
  def apply(raw: UInt): DecodedInstr = {
    val d = Wire(new DecodedInstr)
    d.rawInstr := raw
    d
  }
}