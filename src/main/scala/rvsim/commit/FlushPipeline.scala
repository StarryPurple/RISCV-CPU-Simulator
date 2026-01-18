package rvsim.commit

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class FlushPipeline extends Module {
  val io = IO(new Bundle {
    val robInput = Flipped(new FlushAnnouncer)
    val ifOutput = Flipped(new FlushListener)
    val lsbOutput = Flipped(new FlushListener)
    val rsOutput = Flipped(new FlushListener)
    val duOutput = Flipped(new FlushListener)
  })

  val flushReq = io.robInput.req
  
  io.ifOutput.req  := flushReq
  io.lsbOutput.req := flushReq
  io.rsOutput.req  := flushReq
  io.duOutput.req  := flushReq

  when(flushReq.valid) {
    printf(p"Flush: Now requires flushing. info: ${io.robInput.req.bits}\n")
  }

  val allModulesFlushed = 
    io.ifOutput.flushed && 
    io.lsbOutput.flushed && 
    io.rsOutput.flushed && 
    io.duOutput.flushed

  io.robInput.flushed := flushReq.valid && allModulesFlushed
}