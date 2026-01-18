package rvsim2

import chisel3._
import chisel3.util._

class InstrFetchUnit(val bufSize: Int = 8) extends Module {
  val io = IO(new Bundle {
    val miuInput   = Input(new MIUToIFU)
    val predInput  = Input(new PredToIFU)
    val flushInput = Input(new FlushPipeline)
    val duInput    = Input(new DUToIFU)
    val miuOutput  = Output(new IFUToMIU)
    val predOutput = Output(new IFUToPred)
    val duOutput   = Output(new IFUToDU)
  })

  object State extends ChiselEnum {
    val idle, handleBrJmp = Value
  }

  class Entry extends Bundle {
    val rawInstr    = UInt(32.W)
    val instrAddr   = UInt(Config.XLEN.W)
    val nextPcReady = Bool()
    val nextPc      = UInt(Config.XLEN.W)
  }

  val curStat = RegInit(State.idle)
  val pc      = RegInit(Config.StartAddr.U(Config.XLEN.W))
  val queue   = CircQueue(new Entry, bufSize)

  io.miuOutput  := 0.U.asTypeOf(new IFUToMIU)
  io.predOutput := 0.U.asTypeOf(new IFUToPred)
  io.duOutput   := 0.U.asTypeOf(new IFUToDU)

  val nextStat = WireDefault(curStat)

  when(io.flushInput.isFlush) {
    pc := io.flushInput.pc
    queue.clear()
    io.miuOutput.isValid := true.B
    io.miuOutput.pc      := io.flushInput.pc
    nextStat := State.idle
  } .otherwise {

    val fetchCond = (curStat === State.idle) && !queue.full && (queue.empty || queue.front.instrAddr =/= pc)
    when(fetchCond) {
      io.miuOutput.isValid := true.B
      io.miuOutput.pc      := pc
    }

    when(io.miuInput.isValid && !queue.full) {
      val raw = io.miuInput.rawInstr
      val addr = io.miuInput.instrAddr
      val instr = DecodedInstr(raw)

      val newEntry = Wire(new Entry)
      newEntry.rawInstr    := raw
      newEntry.instrAddr   := addr
      newEntry.nextPcReady := false.B
      newEntry.nextPc      := 0.U

      when(instr.isJal) {
        val targetPc = addr + instr.imm.asUInt
        pc := targetPc
        newEntry.nextPc      := targetPc
        newEntry.nextPcReady := true.B
        nextStat := State.idle
      } .elsewhen(instr.isJalr || instr.isBr) {
        io.predOutput.isValid   := true.B
        io.predOutput.instrAddr := addr
        io.predOutput.isBr      := instr.isBr
        io.predOutput.isJalr    := instr.isJalr
        nextStat := State.handleBrJmp
      } .otherwise {
        val nextSeqPc = addr + 4.U
        pc := nextSeqPc
        newEntry.nextPc      := nextSeqPc
        newEntry.nextPcReady := true.B
        nextStat := State.idle
      }
      queue.push(newEntry)
    }

    when(curStat === State.handleBrJmp && io.predInput.isValid) {
      pc := io.predInput.predPc
      val updatedEntry = queue.back
      updatedEntry.nextPc      := io.predInput.predPc
      updatedEntry.nextPcReady := true.B
      queue.writeAt(queue.backIndex, updatedEntry)
      nextStat := State.idle
    }

    when(io.duInput.canAcceptReq && !queue.empty && queue.front.nextPcReady) {
      val outEntry = queue.front
      io.duOutput.isValid   := true.B
      io.duOutput.rawInstr  := outEntry.rawInstr
      io.duOutput.instrAddr := outEntry.instrAddr
      io.duOutput.predPc    := outEntry.nextPc
      queue.pop()
    }
  }

  curStat := nextStat

  printf(p" pc at ${pc}\n")
}