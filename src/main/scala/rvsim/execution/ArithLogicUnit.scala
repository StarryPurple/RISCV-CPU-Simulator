package rvsim.execution

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class ArithLogicUnit extends Module {
  val io = IO(new Bundle {
    val rsInput = Flipped(new RSToALU)
    val cdbOutput = new CDBSource
  })

  val validReg = RegInit(false.B)
  val e = Reg(new ALUEntry)

  validReg := io.rsInput.entry.valid
  when(io.rsInput.entry.valid) {
    e := io.rsInput.entry.bits
  }

  val op1 = e.src1
  val op2 = Mux(e.useImm, e.imm, e.src2)

  val aluResult = MuxCase(0.U, Seq(
    // ADD / SUB
    (e.funct3 === "b000".U) -> Mux(e.opcode === "b0110011".U && e.funct7(5), op1 - op2, op1 + op2),
    // SLL
    (e.funct3 === "b001".U) -> (op1 << op2(4, 0)),
    // SLT / SLTU
    (e.funct3 === "b010".U) -> Mux(op1.asSInt < op2.asSInt, 1.U, 0.U),
    (e.funct3 === "b011".U) -> Mux(op1 < op2, 1.U, 0.U),
    // XOR
    (e.funct3 === "b100".U) -> (op1 ^ op2),
    // SRL / SRA
    (e.funct3 === "b101".U) -> Mux(e.funct7(5), (op1.asSInt >> op2(4, 0)).asUInt, op1 >> op2(4, 0)),
    // OR / AND
    (e.funct3 === "b110".U) -> (op1 | op2),
    (e.funct3 === "b111".U) -> (op1 & op2)
  ))

  val mulResult = (op1 * op2)

  val divResult = Mux(op2 === 0.U, "hffffffff".U, (op1 / op2))

  val brTaken = MuxCase(false.B, Seq(
    (e.funct3 === "b000".U) -> (op1 === op2),      // BEQ
    (e.funct3 === "b001".U) -> (op1 =/= op2),      // BNE
    (e.funct3 === "b100".U) -> (op1.asSInt < op2.asSInt), // BLT
    (e.funct3 === "b101".U) -> (op1.asSInt >= op2.asSInt),// BGE
    (e.funct3 === "b110".U) -> (op1 < op2),        // BLTU
    (e.funct3 === "b111".U) -> (op1 >= op2)        // BGEU
  ))

  val jumpTarget = op1 + e.imm

  val finalResult = MuxCase(0.U, Seq(
    e.isALU    -> aluResult,
    e.isMUL    -> mulResult,
    e.isDIV    -> divResult,
    e.isJUMP   -> jumpTarget,
    e.isBRANCH -> brTaken.asUInt
  ))

  io.cdbOutput.req.valid        := validReg
  io.cdbOutput.req.bits.data    := finalResult
  io.cdbOutput.req.bits.robIdx  := e.robIdx
  io.cdbOutput.req.bits.destReg := e.destReg
}