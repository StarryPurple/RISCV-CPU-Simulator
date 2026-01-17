/* Related:
  RS -> ALU
 */
package rvsim.bundles

import chisel3._
import chisel3.util._
import rvsim.config.Config

class RSToALU extends Bundle {
  val entry = Valid(new ALUEntry)
}

class ALUEntry extends Bundle {
  val opcode = UInt(7.W)
  val funct3 = UInt(3.W)
  val funct7 = UInt(7.W)
  val src1 = UInt(Config.XLEN.W)
  val src2 = UInt(Config.XLEN.W)                 // if used
  val imm = UInt(Config.XLEN.W)                  // if used
  
  val destReg = UInt(Config.PHYS_REG_ID_WIDTH.W) // Destination physical register (for debug?)
  val archDestReg = UInt(Config.REG_ID_WIDTH.W)  // Architectural destination register (for debug?)
  val robIdx = UInt(Config.ROB_IDX_WIDTH.W)      // Assigned ROB index
  
  val useImm = Bool()                            // use imm / rs2?
  // val isSigned = Bool()
  val pc = UInt(Config.XLEN.W)                   // branch prediction
  val predictedNextPC = UInt(Config.XLEN.W)      // for debug
  
  val isALU = Bool()
  val isMul = Bool()
  val isDiv = Bool()
  val isBranch = Bool()
  val isJump = Bool()
  
  // for debug
  val instr = UInt(32.W)
}