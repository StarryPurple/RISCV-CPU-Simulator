package rvsim2

import chisel3._
import chisel3.util._
class CPUTop extends Module {
  val io = IO(new Bundle {
    val loadPort   = new LoadPort
    val exitCode   = Output(UInt(Config.XLEN.W))
    val terminated = Output(Bool())
  })

  val miu   = Module(new MemoryInterfaceUnit)
  val ifu   = Module(new InstrFetchUnit)
  val du    = Module(new DispatchUnit)
  val rob   = Module(new ReorderBuffer)
  val alu   = Module(new ArithLogicUnit)
  val lsb   = Module(new LoadStoreBuffer)
  val rs    = Module(new ReservationStation)
  val pred  = Module(new Predictor)
  val rf    = Module(new RegisterFile)
  val cdb   = Module(new CommonDataBus)

  val flush = rob.io.flushOutput

  ifu.io.flushInput   := flush
  du.io.flushInput    := flush
  alu.io.flushInput   := flush
  lsb.io.flushInput   := flush
  rs.io.flushInput    := flush
  miu.io.flushInput   := flush

  miu.io.loadPort     := io.loadPort

  ifu.io.miuInput     := miu.io.ifuOutput
  ifu.io.predInput    := pred.io.ifuOutput
  ifu.io.duInput      := du.io.ifuOutput
  miu.io.ifuInput     := ifu.io.miuOutput
  pred.io.ifuInput    := ifu.io.predOutput
  du.io.ifuInput      := ifu.io.duOutput

  du.io.rfInput       := rf.io.duOutput
  du.io.robInput      := rob.io.duOutput
  du.io.cdbInput      := cdb.io.output
  du.io.rsInput       := rs.io.duOutput
  rf.io.duInput       := du.io.rfOutput
  rs.io.duInput       := du.io.rsOutput
  lsb.io.duInput      := du.io.lsbOutput
  rob.io.duInput      := du.io.robOutput

  rs.io.cdbInput      := cdb.io.output
  rs.io.aluInput      := alu.io.rsOutput
  alu.io.rsInput      := rs.io.aluOutput

  lsb.io.miuInput     := miu.io.lsbOutput
  lsb.io.robInput     := rob.io.lsbOutput
  lsb.io.cdbInput     := cdb.io.output
  miu.io.lsbInput     := lsb.io.miuOutput
  rob.io.lsbInput     := lsb.io.robOutput

  cdb.io.lsbInput     := lsb.io.cdbOutput
  cdb.io.aluInput     := alu.io.cdbOutput

  rob.io.dataInput    := cdb.io.output
  rf.io.robInput      := rob.io.rfOutput
  pred.io.robInput    := rob.io.predOutput

  io.terminated       := rob.io.terminated
  io.exitCode         := rf.io.getReg10
}