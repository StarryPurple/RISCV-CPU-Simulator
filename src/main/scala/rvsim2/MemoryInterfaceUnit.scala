package rvsim2

import chisel3._
import chisel3.util._

class MemoryInterfaceUnit(val ramCap: Int = 1 << 20) extends Module {
  val io = IO(new Bundle {
    val lsbInput   = Input(new LSBToMIU)
    val ifuInput   = Input(new IFUToMIU)
    val flushInput = Input(new FlushPipeline)
    val ifuOutput  = Output(new MIUToIFU)
    val lsbOutput  = Output(new MIUToLSB)

    val loadPort   = new LoadPort
  })

  object State extends ChiselEnum {
    val idle, lsbLoad, lsbStore, ifuFetch = Value
  }

  val curStat = RegInit(State.idle)
  
  val addrReg     = RegInit(0.U(Config.XLEN.W))
  val valueReg    = RegInit(0.U(Config.XLEN.W))
  val dataLenReg  = RegInit(0.U(Config.LenWidth))
  val clkDelayReg = RegInit(0.U(32.W))

  // Memory: 4 banks of 8-bit to support byte masking
  val mem = SyncReadMem(ramCap / 4, Vec(4, UInt(8.W)))

  io.ifuOutput := 0.U.asTypeOf(new MIUToIFU)
  io.lsbOutput := 0.U.asTypeOf(new MIUToLSB)

  val nextStat = WireDefault(curStat)
  
  // 1. Pre-loading Logic (LoadPort)
  // This happens independently of the FSM to allow initialization
  when(io.loadPort.en) {
    val loadData = Wire(Vec(4, UInt(8.W)))
    for (i <- 0 until 4) {
      loadData(i) := io.loadPort.data(8 * i + 7, 8 * i)
    }
    mem.write(io.loadPort.addr >> 2, loadData, VecInit(Seq.fill(4)(true.B)))
  }

  // 2. FSM Logic
  switch(curStat) {
    is(State.idle) {
      // Avoid starting new tasks during flush or pre-loading
      when(!io.flushInput.isFlush && !io.loadPort.en) {
        when(io.lsbInput.isLoadRequest) {
          addrReg     := io.lsbInput.addr
          dataLenReg  := io.lsbInput.dataLen
          clkDelayReg := 1.U // Adjusted for single-cycle read trigger
          nextStat    := State.lsbLoad
        }.elsewhen(io.lsbInput.isStoreRequest) {
          addrReg     := io.lsbInput.addr
          dataLenReg  := io.lsbInput.dataLen
          valueReg    := io.lsbInput.value
          clkDelayReg := 1.U 
          nextStat    := State.lsbStore
        }.elsewhen(io.ifuInput.isValid) {
          addrReg     := io.ifuInput.pc
          dataLenReg  := 3.U 
          clkDelayReg := 1.U
          nextStat    := State.ifuFetch
        }
      }
    }

    is(State.lsbLoad) {
      val countdown = clkDelayReg - 1.U
      clkDelayReg := countdown
      // Trigger read in the cycle before the last one
      val rawData = mem.read(addrReg >> 2).asUInt
      when(countdown === 0.U) {
        io.lsbOutput.isLoadReply := true.B
        io.lsbOutput.value       := rawData
        nextStat                 := State.idle
      }
    }

    is(State.lsbStore) {
      val countdown = clkDelayReg - 1.U
      clkDelayReg := countdown
      when(countdown === 0.U) {
        val writeData = Wire(Vec(4, UInt(8.W)))
        val mask      = Wire(Vec(4, Bool()))
        for (i <- 0 until 4) {
          writeData(i) := valueReg(8 * i + 7, 8 * i)
          // Simple mask logic: 0->byte, 1->half, 3->word
          mask(i)      := i.U <= dataLenReg
        }
        mem.write(addrReg >> 2, writeData, mask)
        io.lsbOutput.isStoreReply := true.B
        nextStat                  := State.idle
      }
    }

    is(State.ifuFetch) {
      val countdown = clkDelayReg - 1.U
      clkDelayReg := countdown
      val rawData = mem.read(addrReg >> 2).asUInt
      when(countdown === 0.U) {
        io.ifuOutput.isValid   := true.B
        io.ifuOutput.rawInstr  := rawData
        io.ifuOutput.instrAddr := addrReg
        nextStat               := State.idle
      }
    }
  }

  // 3. Flush Handling
  when(io.flushInput.isFlush) {
    curStat := State.idle
  }.otherwise {
    curStat := nextStat
  }
}