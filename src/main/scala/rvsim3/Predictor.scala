package rvsim3

import chisel3._
import chisel3.util._
import Config._

class PredToIF extends Bundle {
  val predPC = Addr
}

class Predictor extends Module {
  val io = IO(new Bundle {
    val ifIn  = Flipped(Decoupled(new IFToPred))
    val ifOut = Decoupled(new PredToIF)
    val robIn = Flipped(Valid(new RoBToPred)) // always success
  })
  
  val btbSize = 128
  val idxLen = log2Ceil(btbSize)
  def getIdx(pc: UInt) = pc(idxLen + 1, 2)
  // BTB: hash table. last pc taken in this slot
  val btbTarget = Reg(Vec(btbSize, Addr))
  // Tags: last full instrAddr in the correlated slot.
  val btbTags = RegInit(VecInit(Seq.fill(btbSize)(0.U(AddrLen.W))))
  // BHT: 2-bit counter. Initially 01 (not taken)
  val bht = RegInit(VecInit(Seq.fill(btbSize)(1.U(2.W))))

  // RoB update
  when(io.robIn.valid) {
    val updateIdx = getIdx(io.robIn.bits.instrAddr)
    
    btbTarget(updateIdx) := io.robIn.bits.actualPC
    btbTags(updateIdx)   := io.robIn.bits.instrAddr
    
    // 更新 BHT 饱和计数器
    val oldCounter = bht(updateIdx)
    when(io.robIn.bits.actualTaken) {
      bht(updateIdx) := Mux(oldCounter === 3.U, 3.U, oldCounter + 1.U)
    } .otherwise {
      bht(updateIdx) := Mux(oldCounter === 0.U, 0.U, oldCounter - 1.U)
    }
  }

  // --- 2. 预测逻辑 (状态机) ---
  object State extends ChiselEnum {
    val sIdle, sPred = Value
  }
  val state = RegInit(State.sIdle)

  val reqPC = Reg(Addr)

  io.ifIn.ready := (state === State.sIdle)
  io.ifOut.valid := (state === State.sPred)

  val predIdx = getIdx(reqPC)
  val isTaken = btbTags(predIdx) === reqPC && bht(predIdx) >= 2.U

  io.ifOut.bits.predPC := Mux(isTaken, btbTarget(predIdx), reqPC + 4.U)

  switch(state) {
    is(State.sIdle) {
      when(io.ifIn.fire) {
        reqPC := io.ifIn.bits.pc
        state := State.sPred
      }
    }
    is(State.sPred) {
      when(io.ifOut.fire) {
        state := State.sIdle
      }
    }
  }
}