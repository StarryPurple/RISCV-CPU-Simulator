package rvsim2

import chisel3._
import chisel3.util._

class DispatchUnit extends Module {
  val io = IO(new Bundle {
    val ifuInput   = Input(new IFUToDU)
    val rfInput    = Input(new RFToDU)
    val robInput   = Input(new ROBToDU)
    val cdbInput   = Input(new CDBOut)
    val flushInput = Input(new FlushPipeline)
    val rsInput    = Input(new RSToDU)

    val ifuOutput  = Output(new DUToIFU)
    val rsOutput   = Output(new DUToRS)
    val lsbOutput  = Output(new DUToLSB)
    val rfOutput   = Output(new DUToRF)
    val robOutput  = Output(new DUToROB)
  })

  object State extends ChiselEnum {
    val idle, fetchedDecoded, waitRobAlloc, waitOperands, operandsReady, dispatching, stalled = Value
  }

  val state        = RegInit(State.idle)
  val instrValid   = RegInit(false.B)
  val instrRaw     = Reg(UInt(32.W))
  val instrAddr    = Reg(UInt(Config.XLEN.W))
  val nextPc       = Reg(UInt(Config.XLEN.W))
  val src1Ready    = RegInit(false.B)
  val src1Value    = Reg(UInt(Config.XLEN.W))
  val src1Index    = Reg(UInt(Config.ROBIdxWidth))
  val src2Ready    = RegInit(false.B)
  val src2Value    = Reg(UInt(Config.XLEN.W))
  val src2Index    = Reg(UInt(Config.ROBIdxWidth))
  val dstReg       = Reg(UInt(Config.RFIdxWidth))
  val allocRobIdx  = Reg(UInt(Config.ROBIdxWidth))

  val mappingTableReady = RegInit(VecInit(Seq.fill(Config.RFSize)(true.B)))
  val mappingTableIdx   = Reg(Vec(Config.RFSize, UInt(Config.ROBIdxWidth)))

  io.ifuOutput := 0.U.asTypeOf(new DUToIFU)
  io.rsOutput  := 0.U.asTypeOf(new DUToRS)
  io.lsbOutput := 0.U.asTypeOf(new DUToLSB)
  io.rfOutput  := 0.U.asTypeOf(new DUToRF)
  io.robOutput := 0.U.asTypeOf(new DUToROB)

  val decoded = DecodedInstr(instrRaw)

  // Mapping Table Update Logic
  when(io.flushInput.isFlush) {
    state := State.idle
    instrValid := false.B
    mappingTableReady.foreach(_ := true.B)
  } .otherwise {
    // 1. Dependency tracking on allocation
    val robAllocAck = state === State.waitRobAlloc && io.robInput.isAllocValid
    when(robAllocAck && decoded.writeRf && decoded.rd =/= 0.U) {
      mappingTableReady(decoded.rd) := false.B
      mappingTableIdx(decoded.rd)   := io.robInput.robIndex
    }

    // 2. Clear mapping from CDB/Commit
    val cdbLsbValid = io.cdbInput.lsbEntry.isValid
    val cdbAluValid = io.cdbInput.aluEntry.isValid && !io.cdbInput.aluEntry.isLoadStore
    val robCommit   = io.robInput.isCommit

    for (i <- 1 until Config.RFSize) {
      val lsbMatch    = cdbLsbValid && !mappingTableReady(i) && mappingTableIdx(i) === io.cdbInput.lsbEntry.robIndex
      val aluMatch    = cdbAluValid && !mappingTableReady(i) && mappingTableIdx(i) === io.cdbInput.aluEntry.robIndex
      val commitMatch = robCommit   && !mappingTableReady(i) && mappingTableIdx(i) === io.robInput.commitIndex
      
      when(lsbMatch || aluMatch || commitMatch) {
        mappingTableReady(i) := true.B
      }
    }

    // 3. Operand Snooping (In Wait State)
    when(state === State.waitOperands) {
      when(cdbLsbValid) {
        when(!src1Ready && src1Index === io.cdbInput.lsbEntry.robIndex) {
          src1Ready := true.B
          src1Value := io.cdbInput.lsbEntry.value
        }
        when(!src2Ready && src2Index === io.cdbInput.lsbEntry.robIndex) {
          src2Ready := true.B
          src2Value := io.cdbInput.lsbEntry.value
        }
      }
      when(io.cdbInput.aluEntry.isValid) {
        when(!src1Ready && src1Index === io.cdbInput.aluEntry.robIndex) {
          src1Ready := true.B
          src1Value := io.cdbInput.aluEntry.value
        }
        when(!src2Ready && src2Index === io.cdbInput.aluEntry.robIndex) {
          src2Ready := true.B
          src2Value := io.cdbInput.aluEntry.value
        }
      }
    }

    // Main FSM
    switch(state) {
      is(State.idle) {
        io.ifuOutput.canAcceptReq := true.B
        when(io.ifuInput.isValid) {
          instrRaw   := io.ifuInput.rawInstr
          instrAddr  := io.ifuInput.instrAddr
          nextPc     := io.ifuInput.predPc
          instrValid := true.B
          state      := State.waitRobAlloc
        }
      }

      is(State.waitRobAlloc) {
        io.robOutput.isValid   := true.B
        io.robOutput.rawInstr  := instrRaw
        io.robOutput.instrAddr := instrAddr
        // ... other robOutput assignments from decoded ...

        when(io.robInput.isAllocValid) {
          allocRobIdx := io.robInput.robIndex
          
          // Source 1 Logic
          when(!decoded.hasSrc1 || decoded.rs1 === 0.U) {
            src1Ready := true.B
            src1Value := 0.U
          } .elsewhen(io.robInput.hasSrc1) {
            src1Ready := true.B
            src1Value := io.robInput.src1
          } .elsewhen(mappingTableReady(decoded.rs1)) {
            io.rfOutput.isValid := true.B
            io.rfOutput.reqRi   := true.B
            io.rfOutput.Ri      := decoded.rs1
            src1Ready := false.B
          } .otherwise {
            src1Ready := false.B
            src1Index := mappingTableIdx(decoded.rs1)
          }

          // Source 2 Logic
          when(!decoded.hasSrc2 || decoded.rs2 === 0.U) {
            src2Ready := true.B
            src2Value := 0.U
          } .elsewhen(io.robInput.hasSrc2) {
            src2Ready := true.B
            src2Value := io.robInput.src2
          } .elsewhen(mappingTableReady(decoded.rs2)) {
            io.rfOutput.isValid := true.B
            io.rfOutput.reqRj   := true.B
            io.rfOutput.Rj      := decoded.rs2
            src2Ready := false.B
          } .otherwise {
            src2Ready := false.B
            src2Index := mappingTableIdx(decoded.rs2)
          }
          state := State.waitOperands
        }
      }

      is(State.waitOperands) {
        when(io.rfInput.isValid) {
          when(io.rfInput.repRi) { src1Value := io.rfInput.Vi; src1Ready := true.B }
          when(io.rfInput.repRj) { src2Value := io.rfInput.Vj; src2Ready := true.B }
        }
        when(src1Ready && src2Ready) { state := State.operandsReady }
      }

      is(State.operandsReady) {
        when(io.rsInput.canAcceptInstr) {
          io.rsOutput.isValid   := true.B
          io.rsOutput.robIndex  := allocRobIdx
          io.rsOutput.instrType := decoded.instrType
          io.rsOutput.src1Ready := src1Ready
          io.rsOutput.src1Value := src1Value
          io.rsOutput.src1Index := src1Index
          io.rsOutput.src2Ready := src2Ready
          io.rsOutput.src2Value := src2Value
          io.rsOutput.src2Index := src2Index
          io.rsOutput.imm       := decoded.imm
          io.rsOutput.dstReg    := decoded.rd
          io.rsOutput.instrAddr := instrAddr
          io.rsOutput.isBranch  := decoded.isBr || decoded.isJal || decoded.isJalr
          io.rsOutput.predPc    := instrAddr + 4.U

          when(decoded.isLoad || decoded.isStore) {
            io.lsbOutput.isValid   := true.B
            io.lsbOutput.isLoad    := decoded.isLoad
            io.lsbOutput.isStore   := decoded.isStore
            io.lsbOutput.dataLen   := decoded.memDataLen
            io.lsbOutput.robIndex  := allocRobIdx
            io.lsbOutput.dataReady := src2Ready
            io.lsbOutput.dataIndex := src2Index
            io.lsbOutput.dataValue := src2Value
          }
          state := State.dispatching
        } .otherwise {
          state := State.stalled
        }
      }

      is(State.dispatching) {
        state      := State.idle
        instrValid := false.B
      }

      is(State.stalled) {
        when(io.rsInput.canAcceptInstr) { state := State.operandsReady }
      }
    }
  }
}