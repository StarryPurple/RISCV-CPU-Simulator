package rvsim3

import chisel3._
import chisel3.util._
import Config._

class ArithmeticLogicUnit extends Module {
  val io = IO(new Bundle {
    val rsIn   = Flipped(Decoupled(new ALUTask))
    val cdbOut = Decoupled(new CDBPayload)
    val flush  = Flipped(Valid(new FlushPipeline))
  })
  // 状态定义
  object State extends ChiselEnum {
    val sIdle, sBusy, sReady = Value
  }
  val state = RegInit(State.sIdle)

  val execReg    = Reg(new ALUTask)
  val resultReg  = Reg(XData)
  val cycleCount = RegInit(0.U(8.W))
  
  // RS
  io.rsIn.ready := (state === State.sIdle) && !io.flush.valid

  // CDB
  io.cdbOut.valid        := (state === State.sReady) && !io.flush.valid
  io.cdbOut.bits.data    := resultReg
  io.cdbOut.bits.robIdx  := execReg.robIdx
  io.cdbOut.bits.physIdx := execReg.physIdx

  val op1   = execReg.rs1Value
  val op2   = execReg.rs2Value
  val imm   = execReg.decInstr.imm
  val pc    = execReg.decInstr.pc
  val itype = execReg.decInstr.itype

  val aluResult = MuxLookup(itype.asUInt, 0.U)(Seq(
    InstrType.ADD.asUInt   -> (op1 + op2),
    InstrType.ADDI.asUInt  -> (op1 + imm),
    InstrType.SUB.asUInt   -> (op1 - op2),
    InstrType.LUI.asUInt   -> imm,
    InstrType.AUIPC.asUInt -> (pc + imm),
    
    InstrType.XOR.asUInt   -> (op1 ^ op2),
    InstrType.XORI.asUInt  -> (op1 ^ imm),
    InstrType.OR.asUInt    -> (op1 | op2),
    InstrType.ORI.asUInt   -> (op1 | imm),
    InstrType.AND.asUInt   -> (op1 & op2),
    InstrType.ANDI.asUInt  -> (op1 & imm),
    
    InstrType.SLL.asUInt   -> (op1 << op2(4, 0)),
    InstrType.SLLI.asUInt  -> (op1 << imm(4, 0)),
    InstrType.SRL.asUInt   -> (op1 >> op2(4, 0)),
    InstrType.SRLI.asUInt  -> (op1 >> imm(4, 0)),
    InstrType.SRA.asUInt   -> (op1.asSInt >> op2(4, 0)).asUInt,
    InstrType.SRAI.asUInt  -> (op1.asSInt >> imm(4, 0)).asUInt,
    
    InstrType.SLT.asUInt   -> Mux(op1.asSInt < op2.asSInt, 1.U, 0.U),
    InstrType.SLTI.asUInt  -> Mux(op1.asSInt < imm.asSInt, 1.U, 0.U),
    InstrType.SLTU.asUInt  -> Mux(op1 < op2, 1.U, 0.U),
    InstrType.SLTIU.asUInt -> Mux(op1 < imm, 1.U, 0.U),
    
    InstrType.JAL.asUInt   -> (pc + imm),
    InstrType.JALR.asUInt  -> ((op1 + imm) & ~1.U(32.W))
  ))

  switch(state) {
    is(State.sIdle) {
      when(io.rsIn.fire) {
        execReg    := io.rsIn.bits
        cycleCount := io.rsIn.bits.decInstr.calcCycles - 1.U
        state      := State.sBusy
      }
    }
    is(State.sBusy) {
      when(cycleCount > 0.U) {
        cycleCount := cycleCount - 1.U
      } .otherwise {
        resultReg := aluResult
        state     := State.sReady
      }
    }
    is(State.sReady) {
      when(io.cdbOut.fire) {
        state := State.sIdle
      }
    }
  }

  when(io.flush.valid) {
    state      := State.sIdle
    cycleCount := 0.U
  }
}