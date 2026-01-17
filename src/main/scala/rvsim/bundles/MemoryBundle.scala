/* Related:
  IF, LSB -> MI
  MI -> IF, LSB
 */
package rvsim.bundles

import chisel3._
import chisel3.util._
import rvsim.config.Config

object MemoryAccessSize {
  val BYTE = 0.U(2.W)
  val HALF = 1.U(2.W)
  val WORD = 2.U(2.W)
}

// IF, LSB -> MI
class MemoryRequest extends Bundle {
  val req = Decoupled(new MemoryRequestData)
}

class MemoryRequestData extends Bundle {
  val addr = UInt(Config.XLEN.W) 
  val data = UInt(Config.XLEN.W)      // Store
  
  val isWrite = Bool()
  val size = UInt(2.W)                // MemoryAccessSize
  
  // val isUnsigned = Bool()          // Load
  val byteEnable = UInt(4.W)          // Store
  
  val pc = UInt(Config.XLEN.W)        // for debug
  val isInstruction = Bool()          // origin: IF or LSB
}


// MI -> IF, LSB
class MemoryResponse extends Bundle {
  val resp = Decoupled(new MemoryResponseData)
}

class MemoryResponseData extends Bundle {
  val data = UInt(Config.XLEN.W)
}