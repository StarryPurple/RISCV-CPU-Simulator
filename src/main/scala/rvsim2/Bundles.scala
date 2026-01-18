package rvsim2

import chisel3._
import chisel3.util._

// MIU instr load port
class LoadPort extends Bundle {
  val en   = Input(Bool())
  val addr = Input(UInt(32.W))
  val data = Input(UInt(32.W))
}

// --- MIU & IFU ---
class MIUToIFU extends Bundle {
  val isValid    = Bool()
  val rawInstr   = UInt(32.W)
  val instrAddr  = UInt(Config.XLEN.W)
}

class IFUToMIU extends Bundle {
  val isValid = Bool()
  val pc      = UInt(Config.XLEN.W)
}

// --- MIU & LSB ---
class MIUToLSB extends Bundle {
  val isLoadReply  = Bool()
  val isStoreReply = Bool()
  val value        = UInt(Config.XLEN.W)
}

class LSBToMIU extends Bundle {
  val isLoadRequest  = Bool()
  val isStoreRequest = Bool()
  val addr           = UInt(Config.XLEN.W)
  val value          = UInt(Config.XLEN.W)
  val dataLen        = UInt(3.W)
}

// --- IFU & DU & Pred ---
class IFUToDU extends Bundle {
  val isValid   = Bool()
  val rawInstr  = UInt(32.W)
  val instrAddr = UInt(Config.XLEN.W)
  val predPc    = UInt(Config.XLEN.W)
}

class IFUToPred extends Bundle {
  val isValid   = Bool()
  val instrAddr = UInt(Config.XLEN.W)
  val isBr      = Bool()
  val isJalr    = Bool()
}

class DUToIFU extends Bundle {
  val canAcceptReq = Bool()
}

class PredToIFU extends Bundle {
  val isValid = Bool()
  val predPc  = UInt(Config.XLEN.W)
}

// --- ROB & Others ---
class ROBToPred extends Bundle {
  val isValid     = Bool()
  val instrAddr   = UInt(Config.XLEN.W)
  val isPredTaken = Bool()
  val realPc      = UInt(Config.XLEN.W)
  val isBr        = Bool()
}

class ROBToDU extends Bundle {
  val isAllocValid = Bool()
  val robIndex     = UInt(Config.ROBIdxWidth)
  val hasSrc1      = Bool()
  val src1         = UInt(Config.XLEN.W)
  val hasSrc2      = Bool()
  val src2         = UInt(Config.XLEN.W)
  val isCommit     = Bool()
  val commitIndex  = UInt(Config.ROBIdxWidth)
}

class ROBToRF extends Bundle {
  val isValid   = Bool()
  val dstReg    = UInt(Config.RFIdxWidth)
  val value     = UInt(Config.XLEN.W)
  val rawInstr  = UInt(32.W)
}

class ROBToLSB extends Bundle {
  val isValid  = Bool()
  val robIndex = UInt(Config.ROBIdxWidth)
}

class LSBToROB extends Bundle {
  val isValid  = Bool()
  val robIndex = UInt(Config.ROBIdxWidth)
}

class DUToROB extends Bundle {
  val isValid    = Bool()
  val rawInstr   = UInt(32.W)
  val isBr       = Bool()
  val isJalr     = Bool()
  val instrAddr  = UInt(Config.XLEN.W)
  val predPc     = UInt(Config.XLEN.W)
  val isLoad     = Bool()
  val isStore    = Bool()
  val storeAddr  = UInt(Config.XLEN.W)
  val storeValue = UInt(Config.XLEN.W)
  val dataLen    = UInt(3.W)
  val writeRf    = Bool()
  val dstReg     = UInt(Config.RFIdxWidth)
}

// --- CDB ---
class CDBEntry extends Bundle {
  val isValid     = Bool()
  val robIndex    = UInt(Config.ROBIdxWidth)
  val realPc      = UInt(Config.XLEN.W)
  val value       = UInt(Config.XLEN.W)
  val isLoadStore = Bool()
}

class CDBOut extends Bundle {
  val lsbEntry = new CDBEntry
  val aluEntry = new CDBEntry
}

class LSBToCDB extends Bundle {
  val entry = new CDBEntry
}

class ALUToCDB extends Bundle {
  val entry = new CDBEntry
}

// --- Pipeline Control ---
class FlushPipeline extends Bundle {
  val isFlush = Bool()
  val pc      = UInt(Config.XLEN.W)
}

// --- RF & DU ---
class RFToDU extends Bundle {
  val isValid = Bool()
  val repRi   = Bool()
  val repRj   = Bool()
  val Vi      = UInt(Config.XLEN.W)
  val Vj      = UInt(Config.XLEN.W)
}

class DUToRF extends Bundle {
  val isValid = Bool()
  val reqRi   = Bool()
  val reqRj   = Bool()
  val Ri      = UInt(Config.RFIdxWidth)
  val Rj      = UInt(Config.RFIdxWidth)
}

// --- DU & LSB ---
class DUToLSB extends Bundle {
  val isValid   = Bool()
  val dataLen   = UInt(3.W)
  val isLoad    = Bool()
  val isStore   = Bool()
  val dataReady = Bool()
  val dataIndex = UInt(Config.ROBIdxWidth)
  val dataValue = UInt(Config.XLEN.W)
  val robIndex  = UInt(Config.ROBIdxWidth)
}

// --- DU & RS ---
class DUToRS extends Bundle {
  val isValid    = Bool()
  val robIndex   = UInt(Config.ROBIdxWidth)
  val instrType  = Instructions.Type()
  val src1Ready  = Bool()
  val src1Value  = UInt(Config.XLEN.W)
  val src1Index  = UInt(Config.ROBIdxWidth)
  val src2Ready  = Bool()
  val src2Value  = UInt(Config.XLEN.W)
  val src2Index  = UInt(Config.ROBIdxWidth)
  val imm        = SInt(Config.XLEN.W)
  val dstReg     = UInt(Config.RFIdxWidth)
  val instrAddr  = UInt(Config.XLEN.W)
  val isBranch   = Bool()
  val predPc     = UInt(Config.XLEN.W)
}

// --- RS & ALU ---
class RSToALU extends Bundle {
  val isValid    = Bool()
  val robIndex   = UInt(Config.ROBIdxWidth)
  val instrType  = Instructions.Type()
  val src1Value  = UInt(Config.XLEN.W)
  val src2Value  = UInt(Config.XLEN.W)
  val imm        = SInt(Config.XLEN.W)
  val dstReg     = UInt(Config.RFIdxWidth)
  val instrAddr  = UInt(Config.XLEN.W)
  val isBranch   = Bool()
  val predPc     = UInt(Config.XLEN.W)
}

class ALUToRS extends Bundle {
  val canAcceptInstr = Bool()
}

class RSToDU extends Bundle {
  val canAcceptInstr = Bool()
}