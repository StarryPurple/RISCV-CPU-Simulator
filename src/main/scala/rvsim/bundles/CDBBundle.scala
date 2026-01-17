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

// DU, LSB -> CDB
class CDBSource extends Bundle {
  val req = Decoupled(new CDBEntry)
}

// RoB, RS, DU, LSB <- CDB
class CDBListener extends Bundle {
  val in = Input(Valid(new CDBEntry))
}