package rvsim.memory

import chisel3._
import chisel3.util._
import rvsim.config.Config
import rvsim.interfaces._

class MemoryArbiter(numPorts: Int = 2) extends Module {
  val io = IO(new Bundle {
    val in = Vec(numPorts, Flipped(Decoupled(new MemoryRequest)))
    val out = Decoupled(new MemoryRequest)
    val chosen = Output(UInt(log2Ceil(numPorts).W))
  })
  
  val arbiter = Module(new RRArbiter(new MemoryRequest, numPorts))

  for (i <- 0 until numPorts) {
    arbiter.io.in(i) <> io.in(i)
  }
  io.out <> arbiter.io.out
  io.chosen := arbiter.io.chosen
}

// simulates the 3-cycle latency
class MemoryStateMachine extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new MemoryRequest)) // from the arbiter
    val resp = Decoupled(new MemoryResponse)
    val mem = new MemoryArbiterMemBundle
  })

  val idle :: busy :: waitResp :: error :: Nil = Enum(4)
  val state = RegInit(idle)
  val counter = RegInit(0.U(3.W)) // latency countdown.
  val cur = Reg(new MemoryRequest) // record of currently handling req
  
  val respBusy = RegInit(false.B)
  val respData = Reg(new MemoryResponse)
  
  switch(state) {
    is(idle) {
      io.req.ready := true.B
      when(io.req.fire) {
        cur := io.req.bits
        when(io.req.bits.isAligned()) {
          state := busy
          counter := Config.MEM_LATENCY.U
        }.otherwise {
          state := error
        }
      }
    }
    
    is(busy) {
      io.req.ready := false.B
      when(counter > 0.U) {
        counter := counter - 1.U
      }.otherwise {
        io.mem.req.valid := true.B
        io.mem.req.bits.addr := cur.addr
        io.mem.req.bits.data := cur.data
        io.mem.req.bits.write := cur.write
        io.mem.req.bits.mask := cur.byteMask()
        
        when(io.mem.req.fire) {
          when(cur.write) {
            respBusy := true.B
            respData.data := 0.U
            respData.tag := cur.tag
            respData.robIdx := cur.robIdx
            respData.status := 0.U
            state := idle
          }.otherwise {
            state := waitResp
          }
        }
      }
    }
    
    is(waitResp) {
      when(io.mem.resp.fire) {
        val offset = cur.addr(2, 0)
        val shifted = io.mem.resp.bits.data >> (offset * 8.U)
        val data = MuxLookup(cur.size, shifted)(Seq(
          // signed extension
          0.U -> Cat(Fill(56, shifted(7)), shifted(7, 0)),
          1.U -> Cat(Fill(48, shifted(15)), shifted(15, 0)),
          2.U -> Cat(Fill(32, shifted(31)), shifted(31, 0))
        ))
        
        respBusy := true.B
        respData.data := data
        respData.tag := cur.tag
        respData.robIdx := cur.robIdx
        respData.status := 0.U
        
        state := idle
      }
    }
    
    is(error) {
      respBusy := true.B
      respData.data := 0.U
      respData.tag := cur.tag
      respData.robIdx := cur.robIdx
      respData.status := 1.U
      state := idle
    }
  }
  
  io.resp.valid := respBusy
  io.resp.bits := respData
  when(io.resp.fire) {
    respBusy := false.B
  }
  
  io.mem.resp.ready := true.B
}

class MemoryInterface extends Module {
  val io = IO(new Bundle {
    // One for IFU, one for LSB
    val cpu = Vec(2, new Bundle {
      val req = Flipped(Decoupled(new MemoryRequest))
      val resp = Decoupled(new MemoryResponse)
    })
    val mem = new MemoryArbiterMemBundle
  })

  val arb = Module(new MemoryArbiter(2))
  val stm = Module(new MemoryStateMachine)
  
  arb.io.in(0) <> io.cpu(0).req
  arb.io.in(1) <> io.cpu(1).req
  stm.io.req <> arb.io.out
  stm.io.mem <> io.mem
  
  val respSrc = Reg(UInt(1.W))
  val respValid = RegInit(false.B)
  val respData = Reg(new MemoryResponse)
  
  when(arb.io.out.fire) {
    respSrc := arb.io.chosen(0)
  }
  
  when(stm.io.resp.fire) {
    respValid := true.B
    respData := stm.io.resp.bits
  }
  
  for (i <- 0 until 2) {
    io.cpu(i).resp.valid := respValid && (respSrc === i.U)
    io.cpu(i).resp.bits := respData
    io.cpu(i).resp.ready := true.B
  }
  
  when(io.cpu(respSrc).resp.fire) {
    respValid := false.B
  }
}