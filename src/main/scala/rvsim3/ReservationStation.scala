package rvsim3

import chisel3._
import chisel3.util._
import Config._

class RSEntry extends Bundle {
  val decInstr = new DecodedInst
  val robIdx   = RoBIndex
  val physIdx  = PhysIndex

  val rs1Ready   = Bool()
  val rs1PhysIdx = PhysIndex // check this on CDB
  val rs1Value   = XData

  val rs2Ready   = Bool()
  val rs2PhysIdx = PhysIndex // check this on CDB
  val rs2Value   = XData
}

class ALUTask extends Bundle {
  val decInstr = new DecodedInst
  val robIdx   = RoBIndex
  val physIdx  = PhysIndex
  val rs1Value = XData
  val rs2Value = XData
}

class ReservationStation extends Module {
  val io = IO(new Bundle {
    val duIn   = Flipped(Decoupled(new RSEntry))
    val aluOut = Decoupled(new ALUTask)
    val cdbIn  = Flipped(Valid(new CDBPayload))
    val flush  = Flipped(Valid(new FlushPipeline))
  })

  val poolValid = RegInit(VecInit(Seq.fill(NumRSEntries)(false.B)))
  val poolData  = Reg(Vec(NumRSEntries, new RSEntry))

  // DU
  val freeIdx = BinaryPriorityEncoder(poolValid.map(!_))
  val canEnq  = freeIdx.valid && !io.flush.valid

  io.duIn.ready := canEnq
  when(io.duIn.fire) {
    val idx = freeIdx.bits
    val newEntry = io.duIn.bits
    poolValid(idx) := true.B
    poolData(idx)  := newEntry
    
    // and... check CDB.
    when(io.cdbIn.valid) {
      when(!newEntry.rs1Ready && newEntry.rs1PhysIdx === io.cdbIn.bits.physIdx) {
        poolData(idx).rs1Value := io.cdbIn.bits.data
        poolData(idx).rs1Ready := true.B
        printf("[RS] received CDB rs1(at duIn). rsIdx: %d, physIdx: %d, data: %d(%x)\n", idx, io.cdbIn.bits.physIdx, io.cdbIn.bits.data, io.cdbIn.bits.data)
      }
      when(!newEntry.rs2Ready && newEntry.rs2PhysIdx === io.cdbIn.bits.physIdx) {
        poolData(idx).rs2Value := io.cdbIn.bits.data
        poolData(idx).rs2Ready := true.B
        printf("[RS] received CDB rs2(at duIn). rsIdx: %d, physIdx: %d, data: %d(%x)\n", idx, io.cdbIn.bits.physIdx, io.cdbIn.bits.data, io.cdbIn.bits.data)
      }
    }
  }

  // 3. CDB
  when(io.cdbIn.valid) {
    for (i <- 0 until NumRSEntries) {
      when(poolValid(i)) {
        when(!poolData(i).rs1Ready && poolData(i).rs1PhysIdx === io.cdbIn.bits.physIdx) {
          poolData(i).rs1Value := io.cdbIn.bits.data
          poolData(i).rs1Ready := true.B
          printf("[RS] received CDB rs1. rsIdx: %d, physIdx: %d, data: %d(%x)\n", i.U, io.cdbIn.bits.physIdx, io.cdbIn.bits.data, io.cdbIn.bits.data)
        }
        when(!poolData(i).rs2Ready && poolData(i).rs2PhysIdx === io.cdbIn.bits.physIdx) {
          poolData(i).rs2Value := io.cdbIn.bits.data
          poolData(i).rs2Ready := true.B
          printf("[RS] received CDB rs2. rsIdx: %d, physIdx: %d, data: %d(%x)\n", i.U, io.cdbIn.bits.physIdx, io.cdbIn.bits.data, io.cdbIn.bits.data)
        }
      }
    }
  }

  // 4. ALU issue
  val readyMask = VecInit((0 until NumRSEntries).map { i =>
    poolValid(i) && poolData(i).rs1Ready && poolData(i).rs2Ready
  })

  val issueIdx = BinaryPriorityEncoder(readyMask)
  
  io.aluOut.valid := issueIdx.valid && !io.flush.valid
  
  val selectedEntry = poolData(issueIdx.bits)
  io.aluOut.bits.decInstr := selectedEntry.decInstr
  io.aluOut.bits.robIdx   := selectedEntry.robIdx
  io.aluOut.bits.physIdx  := selectedEntry.physIdx
  io.aluOut.bits.rs1Value := selectedEntry.rs1Value
  io.aluOut.bits.rs2Value := selectedEntry.rs2Value

  when(io.aluOut.fire) {
    poolValid(issueIdx.bits) := false.B
  }

  when(io.flush.valid) {
    poolValid.foreach(_ := false.B)
  }
}