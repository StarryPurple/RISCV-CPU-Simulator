/* Related:
  IF -> Decoder
  IF -> Pred
  Pred -> IF
 */

package rvsim.bundles

import chisel3._
import chisel3.util._
import rvsim.config.Config

class IFToDecoder extends Bundle {
  val req = Decoupled(new IFToDecoderRequest)
}

class IFToDecoderRequest extends Bundle {
  val pc = UInt(Config.XLEN.W)
  val instr = UInt(32.W)
  val predPC = UInt(Config.XLEN.W) // next pc to take
}

class IFToPred extends Bundle {
  val req = Decoupled(new IFToPredRequest)
}

class IFToPredRequest extends Bundle {
  val pc = UInt(Config.XLEN.W)
  val instr = UInt(32.W)
}

class PredToIF extends Bundle {
  val resp = Decoupled(new PredToIFResponse)
}

class PredToIFResponse extends Bundle {
  val predTaken = Bool()
  val targetPC = UInt(Config.XLEN.W)
}