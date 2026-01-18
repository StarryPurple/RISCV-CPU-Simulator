package rvsim2

import chisel3._
import chisel3.util._

class ReorderBuffer(val bufSize: Int = Config.ROBSize) extends Module {
  val io = IO(new Bundle {
    val duInput     = Input(new DUToROB)
    val dataInput   = Input(new CDBOut)
    val lsbInput    = Input(new LSBToROB)
    val lsbOutput   = Output(new ROBToLSB)
    val duOutput    = Output(new ROBToDU)
    val predOutput  = Output(new ROBToPred)
    val rfOutput    = Output(new ROBToRF)
    val flushOutput = Output(new FlushPipeline)

    val terminated = Output(Bool())
  })

  object State extends ChiselEnum {
    val idle, flushing = Value
  }

  class Entry extends Bundle {
    val isReady    = Bool()
    val isBr       = Bool()
    val isJalr     = Bool()
    val instrAddr  = UInt(Config.XLEN.W)
    val predPc     = UInt(Config.XLEN.W)
    val realPc     = UInt(Config.XLEN.W)
    val isLoad     = Bool()
    val isStore    = Bool()
    val storeAddr  = UInt(Config.XLEN.W)
    val storeValue = UInt(Config.XLEN.W)
    val dataLen    = UInt(3.W)
    val writeRf    = Bool()
    val dstReg     = UInt(Config.RFIdxWidth)
    val rfValue    = UInt(Config.XLEN.W)
    val rawInstr   = UInt(32.W)
  }

  val queue    = CircQueue(new Entry, bufSize)
  val flushPc  = RegInit(0.U(Config.XLEN.W))
  val state    = RegInit(State.idle)

  io.lsbOutput   := 0.U.asTypeOf(new ROBToLSB)
  io.duOutput    := 0.U.asTypeOf(new ROBToDU)
  io.predOutput  := 0.U.asTypeOf(new ROBToPred)
  io.rfOutput    := 0.U.asTypeOf(new ROBToRF)
  io.flushOutput := 0.U.asTypeOf(new FlushPipeline)
  io.terminated  := false.B

  switch(state) {
    is(State.flushing) {
      printf("\tRoB: Flush\n")
      io.flushOutput.isFlush := true.B
      io.flushOutput.pc      := flushPc
      queue.clear()
      state := State.idle
    }
    is(State.idle) {
      when(io.duInput.isValid && !queue.full) {
        val newEntry = Wire(new Entry)
        newEntry := 0.U.asTypeOf(new Entry)
        newEntry.isBr       := io.duInput.isBr
        newEntry.isJalr     := io.duInput.isJalr
        newEntry.instrAddr  := io.duInput.instrAddr
        newEntry.predPc     := io.duInput.predPc
        newEntry.isLoad     := io.duInput.isLoad
        newEntry.isStore    := io.duInput.isStore
        newEntry.storeAddr  := io.duInput.storeAddr
        newEntry.storeValue := io.duInput.storeValue
        newEntry.dataLen    := io.duInput.dataLen
        newEntry.writeRf    := io.duInput.writeRf
        newEntry.dstReg     := io.duInput.dstReg
        newEntry.rawInstr   := io.duInput.rawInstr
        queue.push(newEntry)
        printf("\tRoB get new Entry, rawInstr %x\n", newEntry.rawInstr)
        
        io.duOutput.isAllocValid := true.B
        io.duOutput.robIndex     := queue.backIndex
      }

      when(io.lsbInput.isValid) {
        val record = queue.at(io.lsbInput.robIndex)
        val updated = WireDefault(record)
        updated.isReady := true.B
        queue.writeAt(io.lsbInput.robIndex, updated)
      }

      val lsbCdb = io.dataInput.lsbEntry
      when(lsbCdb.isValid) {
        val record = queue.at(lsbCdb.robIndex)
        val updated = WireDefault(record)
        updated.isReady := true.B
        when(record.isBr || record.isJalr) { updated.realPc := lsbCdb.realPc }
        when(record.writeRf) { updated.rfValue := lsbCdb.value }
        queue.writeAt(lsbCdb.robIndex, updated)
      }

      val aluCdb = io.dataInput.aluEntry
      when(aluCdb.isValid) {
        val record = queue.at(aluCdb.robIndex)
        when(!(record.isLoad || record.isStore)) {
          val updated = WireDefault(record)
          updated.isReady := true.B
          when(record.isBr || record.isJalr) { updated.realPc := aluCdb.realPc }
          when(record.writeRf) { updated.rfValue := aluCdb.value }
          queue.writeAt(aluCdb.robIndex, updated)
        }
      }

      when(!queue.empty && queue.front.isReady) {
        val head = queue.front
        val isTerminate = head.rawInstr === Config.TerminationInstr.U

        printf("\tRoB commit Entry, rawInstr %x\n", head.rawInstr)

        when(!isTerminate) {
          when(head.isBr || head.isJalr) {
            io.predOutput.isValid     := true.B
            io.predOutput.instrAddr   := head.instrAddr
            io.predOutput.realPc      := head.realPc
            io.predOutput.isBr        := head.isBr
            io.predOutput.isPredTaken := (head.predPc === head.realPc)

            when(head.predPc =/= head.realPc) {
              flushPc  := head.realPc
              state := State.flushing
            } .elsewhen(head.writeRf) {
              io.rfOutput.isValid  := true.B
              io.rfOutput.dstReg   := head.dstReg
              io.rfOutput.value    := head.rfValue
              io.rfOutput.rawInstr := head.rawInstr
              io.duOutput.isCommit := true.B
              io.duOutput.commitIndex := queue.frontIndex
            }
          } .elsewhen(head.isStore || head.isLoad) {
            io.lsbOutput.isValid  := true.B
            io.lsbOutput.robIndex := queue.frontIndex
            when(head.writeRf) {
              io.rfOutput.isValid  := true.B
              io.rfOutput.dstReg   := head.dstReg
              io.rfOutput.value    := head.rfValue
              io.rfOutput.rawInstr := head.rawInstr
              io.duOutput.isCommit := true.B
              io.duOutput.commitIndex := queue.frontIndex
            }
          } .otherwise {
            io.rfOutput.isValid  := true.B
            io.rfOutput.dstReg   := head.dstReg
            io.rfOutput.value    := head.rfValue
            io.rfOutput.rawInstr := head.rawInstr
            io.duOutput.isCommit := true.B
            io.duOutput.commitIndex := queue.frontIndex
          }
        } .otherwise {
          io.rfOutput.isValid  := true.B
          io.rfOutput.rawInstr := head.rawInstr
          io.terminated        := true.B
        }
        queue.pop()
      }
    }
  }
}