package rvsim

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class CPUTop extends Module {
  val mi   = Module(new memory.MemoryInterface)
  val ifu  = Module(new frontend.InstructionFetcher)
  val dec  = Module(new frontend.Decoder)
  val du   = Module(new dispatch.DispatchUnit)
  val lsb  = Module(new execution.LoadStoreBuffer)
  val pred = Module(new frontend.BranchPredictor)
  val rf   = Module(new commit.RegisterFile)
  val rs   = Module(new execution.ReservationStation)
  val rob  = Module(new commit.ReorderBuffer)
  val eu   = Module(new execution.ExecuteUnit)
  val fp   = Module(new commit.FlushPipeline)
  val cdb  = Module(new commit.CommonDataBus)

  // 28 bundles, 28 wirings.

  mi.io.ifInput      <> ifu.io.miOutput
  ifu.io.miInput     <> mi.io.ifOutput
  
  ifu.io.predOutput  <> pred.io.ifInput
  pred.io.ifOutput   <> ifu.io.predInput
  
  ifu.io.decOutput   <> dec.io.ifInput
  dec.io.duOutput    <> du.io.decInput

  du.io.rfOutput     <> rf.io.duInput
  rf.io.duOutput     <> du.io.rfInput
  
  du.io.robOutput    <> rob.io.duInput
  rob.io.duOutput    <> du.io.robInput
  
  du.io.rsOutput     <> rs.io.duInput
  du.io.lsbOutput    <> lsb.io.duInput

  eu.io.rsInput      <> rs.io.euOutput
  
  mi.io.lsbInput     <> lsb.io.miOutput
  lsb.io.miInput     <> mi.io.lsbOutput

  cdb.io.duInput     <> eu.io.cdbOutput
  cdb.io.lsbInput    <> lsb.io.cdbOutput

  rs.io.cdbInput.in  := cdb.io.output
  rob.io.cdbInput.in := cdb.io.output
  du.io.cdbInput.in  := cdb.io.output

  rob.io.rfOutput    <> rf.io.robInput
  rob.io.predOutput  <> pred.io.robInput
  rob.io.lsbOutput   <> lsb.io.robInput

  fp.io.robInput     <> rob.io.flushOutput
  
  ifu.io.flushInput  <> fp.io.ifOutput
  lsb.io.flushInput  <> fp.io.lsbOutput
  rs.io.flushInput   <> fp.io.rsOutput
  du.io.flushInput   <> fp.io.duOutput
}

object GenerateVerilog extends App {
  import _root_.circt.stage.ChiselStage
  ChiselStage.emitSystemVerilogFile(
    new CPUTop,
    Array("--target-dir", "generated")
  )
}