/* Related:
  DU, LSB -> CDB
  CDB -> RoB, RS, DU
 */
package rvsim.bundles

import chisel3._
import chisel3.util._
import rvsim.config.Config

class CDBEntry extends Bundle {
  val data = UInt(Config.XLEN.W)
  val robIdx = UInt(Config.ROB_IDX_WIDTH.W)
  val destReg = UInt(Config.PHYS_REG_ID_WIDTH.W)
}

class CDBArbiterIO(numSources: Int) extends Bundle {
  val sources = Flipped(Vec(numSources, Valid(new CDBEntry)))
  
  val broadcast = Valid(new CDBEntry)
  val selectedIdx = Output(UInt(log2Ceil(numSources).W)) // [0, numSources)
  val hasConflict = Output(Bool())
}

// DU, LSB -> CDB
class CDBSource extends Bundle {
  val req = Valid(new CDBEntry)

  val granted = Input(Bool())
  val busy = Input(Bool())
}

// RoB, RS, DU <- CDB
class CDBListener extends Bundle {
  val in = Input(Valid(new CDBEntry))
}