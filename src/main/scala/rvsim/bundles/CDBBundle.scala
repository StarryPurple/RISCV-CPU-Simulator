/* Related:
  DU, LSB -> CDB
  CDB -> RoB, RS, DU
 */
package rvsim.bundles

import chisel3._
import chisel3.util._
import rvsim.config.Config

class CDBEntry extends Bundle {
}

// DU, LSB -> CDB
class CDBBroadcaster extends Bundle {

}

// CDB -> RoB, RS, DU
class CDBListener extends Bundle {

}