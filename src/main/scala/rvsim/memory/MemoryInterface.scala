package rvsim.memory

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.bundles._
import chisel3.util.experimental.loadMemoryFromFile

class MILoadPort extends Bundle {
  val en   = Bool()
  val addr = UInt(Config.XLEN.W)
  val data = UInt(Config.XLEN.W)
}

class MemoryInterface extends Module {
  val io = IO(new Bundle {
    val ifInput = Flipped(new MemoryRequest)
    val lsbInput = Flipped(new MemoryRequest)
    val ifOutput = new MemoryResponse
    val lsbOutput = new MemoryResponse

    val loadPort = Input(new MILoadPort)
  })

  val mem = SyncReadMem(Config.MEM_SIZE / 4, Vec(4, UInt(8.W))) // divide as bytes

  val loadAddr = io.loadPort.addr(22, 2)
  val loadDataVec = VecInit(Seq.tabulate(4)(i => io.loadPort.data(8*i+7, 8*i)))
  
  val lsbReq = io.lsbInput.req
  val ifReq  = io.ifInput.req
  
  val busy = RegInit(false.B)
  val count = RegInit(0.U(8.W))
  val respTargetIsLsb = RegInit(false.B)
  val readDataReg = Reg(UInt(Config.XLEN.W))
  val isWriteReg = RegInit(false.B)

  lsbReq.ready := !busy && !io.loadPort.en
  ifReq.ready  := !busy && !lsbReq.valid && !io.loadPort.en

  val chooseLsb = lsbReq.valid
  val activeReq = Mux(chooseLsb, lsbReq.bits, ifReq.bits)
  val hasReq = (lsbReq.valid || ifReq.valid) && !io.loadPort.en

  val wordAddr = activeReq.addr(22, 2)
  val writeDataVec = VecInit(Seq.tabulate(4)(i => activeReq.data(8*i+7, 8*i)))
  val byteMask = activeReq.byteEnable.asBools

  
  when(io.loadPort.en) {
    printf("Write instr/data %d at addr %d\n", io.loadPort.data, io.loadPort.addr)
    busy := false.B
    count := 0.U
    mem.write(loadAddr, loadDataVec, VecInit(Seq.fill(4)(true.B)))
  } .elsewhen (!busy && hasReq) {
    busy := true.B
    count := (Config.MEM_LATENCY.U - 1.U)
    respTargetIsLsb := chooseLsb
    isWriteReg := activeReq.isWrite

    when(activeReq.isWrite) {
      mem.write(wordAddr, writeDataVec, byteMask)
    } .otherwise {
      readDataReg := mem.read(wordAddr).asUInt
      printf("MI: read data/instr %d at addr %d\n", readDataReg, wordAddr * 4.U)
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

  // printf("MI Debug: busy=%d, hasReq=%d, ifValid=%d, loadEn=%d\n", 
  //       busy, hasReq, io.ifInput.req.valid, io.loadPort.en)
}