package rvsim3

import chisel3._
import chisel3.util._
import Config._

class ALUToLSQ extends Bundle {
  val addr = Addr
  val physIdx = PhysIndex
  val robIdx = RoBIndex
}

class ArithmeticLogicUnit extends Module {
  val io = IO(new Bundle {
    val rsIn   = Flipped(Decoupled(new ALUTask))
    val cdbOut = Decoupled(new CDBPayload)
    val lsqOut = Valid(new ALUToLSQ)
    val flush  = Flipped(Valid(new FlushPipeline))
  })
  // 状态定义
  object State extends ChiselEnum {
    val sIdle, sBusy, sReady = Value
  }
  val state = RegInit(State.sIdle)

  val execReg    = Reg(new ALUTask)
  val resultDataReg = Reg(XData)
  val resultAddrReg = Reg(Addr)
  val cycleCount = RegInit(0.U(8.W))
  
  // RS
  io.rsIn.ready := (state === State.sIdle) && !io.flush.valid

  val isSL = execReg.decInstr.isLoad || execReg.decInstr.isStore

  // CDB
  io.cdbOut.valid        := (state === State.sReady) && !isSL && !io.flush.valid
  io.cdbOut.bits.data    := resultDataReg
  io.cdbOut.bits.addr    := resultAddrReg
  io.cdbOut.bits.robIdx  := execReg.robIdx
  io.cdbOut.bits.physIdx := execReg.physIdx

  // LSQ
  io.lsqOut.valid        := (state === State.sReady) && isSL && !io.flush.valid
  io.lsqOut.bits.addr    := resultAddrReg
  io.lsqOut.bits.physIdx := execReg.physIdx
  io.lsqOut.bits.robIdx  := execReg.robIdx

  val op1   = execReg.rs1Value
  val op2   = execReg.rs2Value
  val imm   = execReg.decInstr.imm
  val pc    = execReg.decInstr.pc
  val itype = execReg.decInstr.itype

  val dataResult = MuxLookup(itype.asUInt, 0.U)(Seq(
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

    InstrType.JAL.asUInt   -> (pc + 4.U),
    InstrType.JALR.asUInt  -> (pc + 4.U)
  ))
  val addrResult = MuxLookup(itype.asUInt, 0.U)(Seq(
    InstrType.JAL.asUInt   -> (pc + imm),
    InstrType.JALR.asUInt  -> ((op1 + imm) & ~1.U(32.W)),

    InstrType.BEQ.asUInt  -> Mux(op1 === op2, pc + imm, pc + 4.U),
    InstrType.BNE.asUInt  -> Mux(op1 =/= op2, pc + imm, pc + 4.U),
    InstrType.BLT.asUInt  -> Mux(op1.asSInt < op2.asSInt, pc + imm, pc + 4.U),
    InstrType.BGE.asUInt  -> Mux(op1.asSInt >= op2.asSInt, pc + imm, pc + 4.U),
    InstrType.BLTU.asUInt -> Mux(op1 < op2, pc + imm, pc + 4.U),
    InstrType.BGEU.asUInt -> Mux(op1 >= op2, pc + imm, pc + 4.U),
    
    InstrType.LB.asUInt    -> (op1 + imm),
    InstrType.LH.asUInt    -> (op1 + imm),
    InstrType.LW.asUInt    -> (op1 + imm),
    InstrType.LBU.asUInt   -> (op1 + imm),
    InstrType.LHU.asUInt   -> (op1 + imm),
    InstrType.SB.asUInt    -> (op1 + imm),
    InstrType.SH.asUInt    -> (op1 + imm),
    InstrType.SW.asUInt    -> (op1 + imm),
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
      printf("[ALU] busy. robIdx: %d, physIdx: %d, instr: %x\n", execReg.robIdx, execReg.physIdx, execReg.decInstr.inst)
      when(cycleCount > 0.U) {
        cycleCount := cycleCount - 1.U
      } .otherwise {
        resultDataReg := dataResult
        resultAddrReg := addrResult
        state         := State.sReady
      }
    }
    is(State.sReady) {
      printf("[ALU] ready. value: %d(%x), addr: %d(%x), robIdx: %d, physIdx: %d, instr: %x\n",
             resultDataReg, resultDataReg, resultAddrReg, resultAddrReg, execReg.robIdx, execReg.physIdx, execReg.decInstr.inst)
      printf("[ALU] ready: pc = %d, op1 = %d(%x), op2 = %d(%x), imm = %d(%x)\n", pc, op1, op1, op2, op2, imm, imm)
      when((!isSL && io.cdbOut.fire) || (isSL && io.lsqOut.fire)) {
        state := State.sIdle
      }
    }
  }

  when(io.flush.valid) {
    state      := State.sIdle
    cycleCount := 0.U
  }
}