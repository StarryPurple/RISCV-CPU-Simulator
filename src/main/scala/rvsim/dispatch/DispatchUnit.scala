package rvsim.dispatch

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

// Register Alias Table (RAT) - maps architectural registers to physical registers
class RegisterAliasTable extends Module {
  val io = IO(new Bundle {
    val rs1 = Input(UInt(5.W))
    val rs2 = Input(UInt(5.W))
    val rs1_tag  = Output(UInt(Config.ROB_ID_WIDTH.W))
    val rs1_wait = Output(Bool())
    val rs2_tag  = Output(UInt(Config.ROB_ID_WIDTH.W))
    val rs2_wait = Output(Bool())

    // add mapping when writing new archReg
    val update_en = Input(Bool())
    val rd        = Input(UInt(5.W))
    val robIdx    = Input(UInt(Config.ROB_ID_WIDTH.W))

    // delete mapping when commiting
    val commit_en = Input(Bool())
    val commit_rd = Input(UInt(5.W))
    val commit_robIdx = Input(UInt(Config.ROB_ID_WIDTH.W))

    val flush = Input(Bool())
  })

  // RegIdx -> robIdx
  val tagTable = RegInit(VecInit(Seq.fill(32)(0.U(Config.ROB_ID_WIDTH.W))))
  // That Register State Table (RST). Is reg still busy?
  val busyTable = RegInit(VecInit(Seq.fill(32)(false.B)))

  // x0 always return 0 and is not busy
  io.rs1_tag  := Mux(io.rs1 === 0.U, 0.U, tagTable(io.rs1))
  io.rs1_wait := Mux(io.rs1 === 0.U, false.B, busyTable(io.rs1))
  
  io.rs2_tag  := Mux(io.rs2 === 0.U, 0.U, tagTable(io.rs2))
  io.rs2_wait := Mux(io.rs2 === 0.U, false.B, busyTable(io.rs2))

  when(io.flush) {
    busyTable.foreach(_ := false.B)
  } .otherwise {
    when(io.commit_en && io.commit_rd =/= 0.U) {
      when(tagTable(io.commit_rd) === io.commit_robIdx) {
        busyTable(io.commit_rd) := false.B
      }
    }
    // updata is prior to commit
    when(io.update_en && io.rd =/= 0.U) {
      tagTable(io.rd)  := io.robIdx
      busyTable(io.rd) := true.B
    }
  }
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