/* Related:
  Decoder -> DU
  RF -> DU

  DU -> RF
  DU -> RS
  DU -> LSB
 */
package rvsim.bundles

import chisel3._
import chisel3.util._
import rvsim.config.Config

class DecoderToDU extends Bundle {
  val decodedInstr = Decoupled(new DecodedInstrInfo)
}

class DecodedInstrInfo extends Bundle {
  val pc = UInt(Config.XLEN.W)              // Instruction PC
  val instr = UInt(32.W)                    // Raw instruction word

  val predPC = UInt(Config.XLEN.W)          // predict of next pc
  
  // Decoded fields
  val opcode = UInt(7.W)                    // Opcode
  val rd = UInt(5.W)                        // Destination register (architectural)
  val rs1 = UInt(5.W)                       // Source register 1 (architectural)
  val rs2 = UInt(5.W)                       // Source register 2 (architectural)
  val imm = UInt(Config.XLEN.W)             // Sign-extended immediate
  val funct3 = UInt(3.W)                    // Funct3 field
  val funct7 = UInt(7.W)                    // Funct7 field

  val useImm = Bool()
  
  // Instruction type flags
  val isALU = Bool()                        // ALU instruction
  val isMul = Bool()                        // Multiply instruction
  val isDiv = Bool()                        // Divide instruction
  val isBranch = Bool()                     // Branch instruction
  val isJump = Bool()                       // Jump instruction
  val isLoad = Bool()                       // Load instruction
  val isStore = Bool()                      // Store instruction
  
  // Control signals
  val needsRs1 = Bool()                     // Requires rs1 operand
  val needsRs2 = Bool()                     // Requires rs2 operand
  val writesRd = Bool()                     // Writes to rd register
}

class DUToRF extends Bundle {
  // arch reg for rs1 and rs2
  val readReq = Vec(2, Valid(UInt(5.W)))
}

class RFToDU extends Bundle {
  // rs1/rs2 read response
  val readRsp = Vec(2, new Bundle {
    val data = UInt(Config.XLEN.W)
  })
}

class DUToRS extends Bundle {
  val allocReq = Decoupled(new RSEntry)        // RS entry allocation request
}

class RSEntry extends Bundle {
  // Instruction information
  val opcode = UInt(7.W)
  val funct3 = UInt(3.W)
  val funct7 = UInt(7.W)
  val imm = UInt(Config.XLEN.W)
  
  // Operand information
  val src1 = new OperandInfo
  val src2 = new OperandInfo
  
  // Destination information
  val destReg = UInt(Config.PHYS_REG_ID_WIDTH.W) // Destination physical register
  val archDestReg = UInt(5.W)                    // Architectural destination register
  val robIdx = UInt(Config.ROB_IDX_WIDTH.W)      // Assigned ROB index
  
  // Instruction type
  // val instrType = UInt(3.W)                   // 0:ALU, 1:MUL, 2:DIV, 3:BRANCH
  
  // Debug information
  val pc = UInt(Config.XLEN.W)
  val instr = UInt(32.W)
  
  // sync with ALUEntry
  val useImm = Bool()                            // use imm / rs2?
  val predictedNextPC = UInt(Config.XLEN.W)
  
  val isALU = Bool()
  val isMul = Bool()
  val isDiv = Bool()
  val isBranch = Bool()
  val isJump = Bool()
}

class OperandInfo extends Bundle {
  val data = UInt(Config.XLEN.W)                 // Operand value (if ready)
  val ready = Bool()                             // Data is ready
  val robIdx = UInt(Config.ROB_IDX_WIDTH.W)      // RoB index if waiting for result
}

// Dispatch Unit -> Load/Store Buffer
class DUToLSB extends Bundle {
  val allocReq = Decoupled(new LSBEntry)        // LSB entry allocation request
}

class LSBEntry extends Bundle {
  val isLoad = Bool()                           // Is load instruction
  val isStore = Bool()                          // Is store instruction
  
  // Memory access details
  val size = UInt(2.W)                          // 0:byte, 1:half, 2:word
  val signed = Bool()                           // Sign-extend for loads
  
  // Address information
  val addr = new Bundle {
    val base = UInt(Config.XLEN.W)              // Base address
    val offset = SInt(12.W)                     // Offset. Must be ready.
    val baseReady = Bool()                      // Base address is ready
    val baseRobIdx = UInt(Config.ROB_IDX_WIDTH.W) // RoB index if waiting for base addr.
  }
  
  // Store data (for store instructions)
  val storeData = new Bundle {
    val value = UInt(Config.XLEN.W)             // Data to store
    val ready = Bool()                          // Store data is ready
  }
  
  // Destination (for load instructions)
  val destReg = UInt(Config.PHYS_REG_ID_WIDTH.W) // Destination physical register (for debug?)
  val archDestReg = UInt(5.W)                    // Architectural destination register (for debug?)
  val robIdx = UInt(Config.ROB_IDX_WIDTH.W)      // Assigned ROB index
  
  // Debug information
  val pc = UInt(Config.XLEN.W)
  val instr = UInt(32.W)
}