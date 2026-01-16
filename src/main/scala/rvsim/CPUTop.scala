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

  
}