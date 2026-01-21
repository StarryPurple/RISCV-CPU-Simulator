package rvsim3

import chisel3._
import chisel3.util._

object BinaryPriorityEncoder {
  def apply(input: Bits): Valid[UInt] = {
    require(input.getWidth > 0)
    val roundedWidth = 1 << log2Ceil(input.getWidth)
    val roundedInput = WireDefault(0.U(roundedWidth.W))
    roundedInput := input

    def select(in: UInt): Valid[UInt] = {
      val width = in.getWidth
      val resWidth = log2Ceil(width)
      val res = Wire(Valid(UInt(resWidth.W)))
      if(width == 1) {
        res.valid := in.asBool
        res.bits  := 0.U
      } else {
        val low = in(width / 2 - 1, 0)
        val high = in(width - 1, width / 2)

        val lowRes = select(low)
        val highRes = select(high)

        res.valid := lowRes.valid || highRes.valid
        res.bits  := Mux(lowRes.valid, Cat(0.U(1.W), lowRes.bits), Cat(1.U(1.W), highRes.bits)) 
      }
      res
    }
    select(roundedInput)
  }
  def apply(in: Seq[Bool]): Valid[UInt] = {
    apply(VecInit(in).asUInt)
  }
}