package rvsim3

import chisel3._
import chisel3.util._
import Config._

class CentralProcessingUnit extends Module {
  val io = IO(new Bundle {
    val preload = Input(new PreloadBus)
    
    val returnVal = Output(XData)
    val isTerminated = Output(Bool())
  })

  val pred = Module(new Predictor)
  val ifu  = Module(new InstructionFetcher)
  val dec  = Module(new Decoder)
  val du   = Module(new DispatchUnit)
  val rob  = Module(new ReorderBuffer)
  val fl   = Module(new FreeList)
  val rf   = Module(new RegisterFile)
  val rs   = Module(new ReservationStation)
  val lsq  = Module(new LoadStoreQueue)
  val alu  = Module(new ArithmeticLogicUnit)
  val cdb  = Module(new CommonDataBus(numSources = 2)) // 0: ALU, 1: LSQ
  val mi   = Module(new MemoryInterface)
  val ram  = Module(new RandomAccessMemory)

  pred.io.ifOut  <> ifu.io.predIn
  ifu.io.predOut <> pred.io.ifIn

  ifu.io.decOut <> dec.io.ifIn

  dec.io.duOut <> du.io.decIn

  du.io.robIn <> rob.io.duOut
  du.io.flIn  <> fl.io.duOut
  du.io.flOut <> fl.io.duIn

  rs.io.duIn  <> du.io.rsOut
  lsq.io.duIn <> du.io.lsqOut
  rob.io.duIn <> du.io.robOut

  alu.io.rsIn <> rs.io.aluOut

  // CDB Broadcast
  cdb.io.in(0) <> alu.io.cdbOut
  cdb.io.in(1) <> lsq.io.cdbOut

  val globalCDB = cdb.io.out // Valid(new CDBPayload)

  rob.io.cdbIn  := globalCDB
  rs.io.cdbIn   := globalCDB
  lsq.io.cdbIn  := globalCDB
  rf.io.cdbIn   := globalCDB

  rf.io.duIn <> du.io.rfOut
  du.io.rfIn := rf.io.duOut

  mi.io.ifIn  <> ifu.io.miOut
  mi.io.lsqIn <> lsq.io.miOut
  
  ifu.io.miIn <> mi.io.ifOut
  lsq.io.miIn     <> mi.io.lsqOut

  ram.io.in      <> mi.io.ramOut
  mi.io.ramIn    <> ram.io.out
  ram.io.preload := io.preload

  fl.io.robIn <> rob.io.flOut

  pred.io.robIn := rob.io.predOut

  lsq.io.robIn := rob.io.lsqOut

  val globalFlush = rob.io.flushOut

  ifu.io.flush := globalFlush
  dec.io.flush := globalFlush
  rs.io.flush  := globalFlush
  lsq.io.flush := globalFlush
  alu.io.flush := globalFlush
  mi.io.flush  := globalFlush

  // result check
  rf.io.physIdxQuery := du.io.x10PhysIdx
  io.returnVal       := rf.io.valueQuery
  io.isTerminated    := rob.io.isTerminated
}