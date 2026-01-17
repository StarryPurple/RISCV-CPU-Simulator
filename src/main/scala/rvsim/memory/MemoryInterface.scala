package rvsim.memory

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._

class MemoryInterface extends Module {
  val io = IO(new Bundle {
    val ifInput = Flipped(new MemoryRequest)
    val lsbInput = Flipped(new MemoryRequest)
    val ifOutput = new MemoryResponse
    val lsbOutput = new MemoryResponse

    val loadPort = Flipped(new Bundle {
      val en   = Input(Bool())
      val addr = Input(UInt(Config.XLEN.W))
      val data = Input(UInt(Config.DATA_WIDTH.W))
    })
  })

  val mem = SyncReadMem(Config.MEM_SIZE / 4, Vec(4, UInt(8.W))) // divide as bytes
  
  val lsbReq = io.lsbInput.req
  val ifReq  = io.ifInput.req
  val chooseLsb = lsbReq.valid
  val activeReq = Mux(chooseLsb, lsbReq.bits, ifReq.bits)
  val hasReq = lsbReq.valid || ifReq.valid

  val busy = RegInit(false.B)
  lsbReq.ready := !busy && !io.loadPort.en
  ifReq.ready  := !busy && !io.loadPort.en && !lsbReq.valid

  val count = RegInit(0.U(8.W))
  val respPending = RegInit(false.B)
  val respTargetIsLsb = RegInit(false.B)
  val readDataReg = Reg(UInt(Config.XLEN.W))
  
  // remove last two bits to get index(which byte) in mem.
  val wordAddr = activeReq.addr(22, 2)
  val writeDataVec = VecInit(Seq.tabulate(4)(i => activeReq.data(8*i+7, 8*i)))
  val byteMask = activeReq.byteEnable.asBools

  when(io.loadPort.en) {
    // preload has highest priority
    mem.write(io.loadPort.addr(22, 2), 
              VecInit(Seq.tabulate(4)(i => io.loadPort.data(8*i+7, 8*i))), 
              Seq.fill(4)(true.B))
  } .otherwise {
    when(!busy && hasReq) {
      busy := true.B
      count := (Config.MEM_LATENCY.U - 1.U)
      respPending := true.B
      respTargetIsLsb := chooseLsb

      when(activeReq.isWrite) {
        mem.write(wordAddr, writeDataVec, byteMask)
      } .otherwise {
        readDataReg := mem.read(wordAddr).asUInt
      }
    }
  }

  when(busy) {
    when(count === 0.U) {
      busy := false.B
    } .otherwise {
      count := count - 1.U
    }
  }

  // Send response at count just decreased to 0
  val toResp = busy && (count === 0.U)

  io.lsbOutput.resp.valid := toResp && respTargetIsLsb
  io.lsbOutput.resp.bits.data := readDataReg

  io.ifOutput.resp.valid := toResp && !respTargetIsLsb
  io.ifOutput.resp.bits.data := readDataReg

  // Decoupled unlock
  io.lsbOutput.resp.ready := true.B
  io.ifOutput.resp.ready := true.B
}