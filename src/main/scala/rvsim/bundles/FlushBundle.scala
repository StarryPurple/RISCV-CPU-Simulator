/* Related:
  RoB -> Flush
  Flush -> IF, LSB, RS, DU
 */
package rvsim.bundles

import chisel3._
import chisel3.util._
import rvsim.config.Config

// RoB -> Flush
class FlushAnnouncer extends Bundle {
  val req = Valid(new FlushRequest)
  val flushed = Input(Bool())  // end flush at all modules finished flushing
}

// IF, LSB, RS, DU <- Flush
class FlushListener extends Bundle {
  val req = Flipped(Valid(new FlushRequest))
  val flushed = Output(Bool())  // end flush at all modules finished flushing
}

class FlushRequest extends Bundle {
  val targetPC = UInt(Config.XLEN.W)
  val flushFromRoBIdx = Valid(UInt(Config.ROB_IDX_WIDTH.W))
  val mispredictedPC = UInt(Config.XLEN.W)    // for debug
  val isBranch = Bool()                       // for debug
  val isJump = Bool()                         // for debug
}