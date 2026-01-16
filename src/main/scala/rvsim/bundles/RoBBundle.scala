/* Related:
  RoB -> Pred
  RoB -> RF
  RoB -> LSB
  RoB -> DU

  DU -> RoB
 */
package rvsim.bundles

import chisel3._
import chisel3.util._
import rvsim.config.Config

class RoBToPred extends Bundle {
  val info = Valid(new BranchCommitInfo)
}

class BranchCommitInfo extends Bundle {
  val pc = UInt(Config.XLEN.W)
  val target = UInt(Config.XLEN.W)
  val taken = Bool()
  val isBranch = Bool()
  val isJump = Bool()
  val isReturn = Bool()
}

class RoBToRF extends Bundle {
  val regWrite = Valid(new RegWriteInfo)
}

class RegWriteInfo extends Bundle {
  val rd = UInt(Config.REG_ID_WIDTH.W)
  val data = UInt(Config.XLEN.W)
  
  val robIdx = UInt(Config.ROB_IDX_WIDTH.W)
  val pc = UInt(Config.XLEN.W)
}

class RoBToLSB extends Bundle {
  val storeCommit = Valid(new StoreCommitInfo)
}

class StoreCommitInfo extends Bundle {
  val robIdx = UInt(Config.ROB_IDX_WIDTH.W)
}

class RoBToDU extends Bundle {
  val allocResp = Decoupled(new RoBAllocResp)
}

class RoBAllocResp extends Bundle {
  val robIdx = UInt(Config.ROB_IDX_WIDTH.W)
}

class DUToRoB extends Bundle {
  val allocReq = Decoupled(new RoBAllocReq)
}

class RoBAllocReq extends Bundle {
  val pc = UInt(Config.XLEN.W)              // Instruction PC
  val instr = UInt(32.W)                    // Raw instruction word
  
  // Decoded fields
  val opcode = UInt(7.W)                    // Opcode
  val rd = UInt(5.W)                        // Destination register (architectural)
  val rs1 = UInt(5.W)                       // Source register 1 (architectural)
  val rs2 = UInt(5.W)                       // Source register 2 (architectural)
  val imm = UInt(Config.XLEN.W)             // Sign-extended immediate
  val funct3 = UInt(3.W)                    // Funct3 field
  val funct7 = UInt(7.W)                    // Funct7 field
}