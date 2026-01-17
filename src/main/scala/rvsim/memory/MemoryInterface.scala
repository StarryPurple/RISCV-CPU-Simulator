package rvsim.memory

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._
import chisel3.util.experimental.loadMemoryFromFile

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
  loadMemoryFromFile(mem, "src/main/resources/program.hex")
  
  val lsbReq = io.lsbInput.req
  val ifReq  = io.ifInput.req
  
  val busy = RegInit(false.B)
  val count = RegInit(0.U(8.W))
  val respTargetIsLsb = RegInit(false.B)
  val readDataReg = Reg(UInt(Config.XLEN.W))
  val isWriteReg = RegInit(false.B)

  lsbReq.ready := !busy && !io.loadPort.en
  ifReq.ready  := !busy && !io.loadPort.en && !lsbReq.valid

  val chooseLsb = lsbReq.valid
  val activeReq = Mux(chooseLsb, lsbReq.bits, ifReq.bits)
  val hasReq = lsbReq.valid || ifReq.valid

  val wordAddr = activeReq.addr(22, 2)
  val writeDataVec = VecInit(Seq.tabulate(4)(i => activeReq.data(8*i+7, 8*i)))
  val byteMask = activeReq.byteEnable.asBools

  when(io.loadPort.en) {
    mem.write(io.loadPort.addr(22, 2), 
              VecInit(Seq.tabulate(4)(i => io.loadPort.data(8*i+7, 8*i))), 
              Seq.fill(4)(true.B))
  } .otherwise {
    when(!busy && hasReq) {
      busy := true.B
      count := (Config.MEM_LATENCY.U - 1.U)
      respTargetIsLsb := chooseLsb
      isWriteReg := activeReq.isWrite

      when(activeReq.isWrite) {
        mem.write(wordAddr, writeDataVec, byteMask)
      } .otherwise {
        readDataReg := mem.read(wordAddr).asUInt
      }
    }
  }

  val currentRespValid = busy && (count === 0.U)
  val currentReady = Mux(respTargetIsLsb, io.lsbOutput.resp.ready, io.ifOutput.resp.ready)

  when(busy) {
    when(count === 0.U) {
      when(currentReady || isWriteReg) {
        busy := false.B
      }
    } .otherwise {
      count := count - 1.U
    }
  }

  io.lsbOutput.resp.valid := currentRespValid && respTargetIsLsb
  io.lsbOutput.resp.bits.data := readDataReg

  io.ifOutput.resp.valid := currentRespValid && !respTargetIsLsb
  io.ifOutput.resp.bits.data := readDataReg
}