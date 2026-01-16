package rvsim.dispatch

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

// Register Alias Table (RAT) - maps architectural registers to physical registers
class RegisterAliasTable extends Module {
  val io = IO(new Bundle {
    
  })
  
}

// Register Status Table - tracks readiness of physical registers
class RegisterStatusTable extends Module {
  val io = IO(new Bundle {
    
  })
  
}

class DispatchUnit extends Module {
  val io = IO(new Bundle {
    val decInput = Flipped(new DecoderToDU)
    val rfInput = Flipped(new RFToDU)
    val robInput = Flipped(new RoBToDU)
    val flushInput = new FlushListener
    val cdbInput = new CDBListener
    val rfOutput = new DUToRF
    val robOutput = new DUToRoB
    val rsOutput = new DUToRS
    val lsbOutput = new DUToLSB
  })
}