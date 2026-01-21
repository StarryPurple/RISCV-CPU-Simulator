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

class ReservationStation(numRSEntries: Int) extends Module {
  val io = IO(new Bundle {
    val duIn   = Flipped(Decoupled(new RSEntry))
    val aluOut = Decoupled(new ALUTask)
    val cdbIn  = Flipped(Valid(new CDBPayload))
    val flush  = Flipped(Valid(new FlushPipeline))
  })

  val poolValid = RegInit(VecInit(Seq.fill(numRSEntries)(false.B)))
  val poolData  = Reg(Vec(numRSEntries, new RSEntry))

  // DU
  val freeIdx = BinaryPriorityEncoder(poolValid.map(!_))
  val canEnq  = freeIdx.valid && !io.flush.valid

  io.duIn.ready := canEnq
  when(io.duIn.fire) {
    val idx = freeIdx.bits
    poolValid(idx) := true.B
    poolData(idx)  := io.duIn.bits
  }

  // 3. CDB
  when(io.cdbIn.valid) {
    for (i <- 0 until numRSEntries) {
      when(poolValid(i)) {
        when(!poolData(i).rs1Ready && poolData(i).rs1PhysIdx === io.cdbIn.bits.physIdx) {
          poolData(i).rs1Value := io.cdbIn.bits.data
          poolData(i).rs1Ready := true.B
        }
        when(!poolData(i).rs2Ready && poolData(i).rs2PhysIdx === io.cdbIn.bits.physIdx) {
          poolData(i).rs2Value := io.cdbIn.bits.data
          poolData(i).rs2Ready := true.B
        }
      }
    }
  }

  // 4. ALU issue
  val readyMask = VecInit((0 until numRSEntries).map { i =>
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