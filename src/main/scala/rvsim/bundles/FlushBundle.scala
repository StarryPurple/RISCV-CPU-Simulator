/* Related:
  RoB -> Flush
  Flush -> IF, LSB, RS, DU
 */
package rvsim.bundles

import chisel3._
import chisel3.util._
import rvsim.config.Config

// RoB -> Flush
class FlushBroadcaster extends Bundle {

}

// Flush -> IF, LSB, RS, DU
class FlushListener extends Bundle {
  
}