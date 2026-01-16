/* Related:
  RS -> EU
 */
package rvsim.bundles

import chisel3._
import chisel3.util._
import rvsim.config.Config

class RSToEU extends Bundle {
  val entry = Valid(new EUEntry)
}

class EUEntry extends Bundle {
  val opcode = UInt(7.W)
  val funct3 = UInt(3.W)
  val funct7 = UInt(7.W)
  val isALU = Bool()
  val isMUL = Bool()
  val isDIV = Bool()
  val isBRANCH = Bool()
  val isJUMP = Bool()
  
  val src1 = UInt(Config.XLEN.W)
  val src2 = UInt(Config.XLEN.W)                 // if used
  val imm = UInt(Config.XLEN.W)                  // if used
  
  val destReg = UInt(Config.PHYS_REG_ID_WIDTH.W) // Destination physical register (for debug?)
  val archDestReg = UInt(Config.REG_ID_WIDTH.W)  // Architectural destination register (for debug?)
  val robIdx = UInt(Config.ROB_IDX_WIDTH.W)      // Assigned ROB index
  
  val useImm = Bool()                            // use imm / rs2?
  val isSigned = Bool()
  val pc = UInt(Config.XLEN.W)                   // branch prediction
  val predictedNextPC = UInt(Config.XLEN.W)      // for debug
  
  // for debug
  val instr = UInt(32.W)
}