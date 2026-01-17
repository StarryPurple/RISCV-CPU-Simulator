package rvsim.commit

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class RegisterFile extends Module {
  val io = IO(new Bundle {
    val duInput = Flipped(new DUToRF)
    val robInput = Flipped(new RoBToRF)
    val duOutput = new RFToDU

    val debug_x10 = Output(UInt(Config.XLEN.W))
  })

  // x0 ~ x31
  val regs = RegInit(VecInit(Seq.fill(32)(0.U(Config.XLEN.W))))

  io.duOutput.readRsp(0).data := Mux(io.duInput.readReq(0).bits === 0.U, 0.U, regs(io.duInput.readReq(0).bits))
  io.duOutput.readRsp(1).data := Mux(io.duInput.readReq(1).bits === 0.U, 0.U, regs(io.duInput.readReq(1).bits))

  val commit = io.robInput.regWrite
  when(commit.valid && commit.bits.rd =/= 0.U) {
    regs(commit.bits.rd) := commit.bits.data
  }

  io.debug_x10 := regs(10)
}