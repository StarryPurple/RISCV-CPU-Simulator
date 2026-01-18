package rvsim2

import chisel3._
import chisel3.util._

class CommonDataBus extends Module {
  val io = IO(new Bundle {
    val lsbInput = Input(new LSBToCDB)
    val aluInput = Input(new ALUToCDB)
    val output   = Output(new CDBOut)
  })

  io.output.lsbEntry := 0.U.asTypeOf(new CDBEntry)
  io.output.aluEntry := 0.U.asTypeOf(new CDBEntry)

  when(io.lsbInput.entry.isValid) {
    io.output.lsbEntry := io.lsbInput.entry
  }

  when(io.aluInput.entry.isValid) {
    io.output.aluEntry := io.aluInput.entry
  }
}