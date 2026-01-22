package rvsim3

import chisel3._
import chisel3.util._
import Config._

class RFToDU extends Bundle {
  val rs1Value = XData
  val rs1Ready = Bool()

  val rs2Value = XData
  val rs2Ready = Bool()
}

class RegisterFile extends Module {
  val io = IO(new Bundle {
    val duIn  = Input(new DUToRF)
    val duOut = Output(new RFToDU)
    val cdbIn = Flipped(Valid(new CDBPayload))

    val physIdxQuery = Input(PhysIndex)
    val valueQuery   = Output(XData)
  })

  val dataArray  = RegInit(VecInit(Seq.fill(NumPhysRegs)(0.U(XDataLen.W))))
  val readyArray = RegInit(VecInit(Seq.tabulate(NumPhysRegs)(i => (i < NumArchRegs).B)))

  // 2. CDB Bypass
  val rs1CdbHit = io.cdbIn.valid && io.cdbIn.bits.physIdx === io.duIn.rs1PhysIdx
  io.duOut.rs1Value := Mux(rs1CdbHit, io.cdbIn.bits.data, dataArray(io.duIn.rs1PhysIdx))
  io.duOut.rs1Ready := readyArray(io.duIn.rs1PhysIdx) || rs1CdbHit

  val rs2CdbHit = io.cdbIn.valid && io.cdbIn.bits.physIdx === io.duIn.rs2PhysIdx
  io.duOut.rs2Value := Mux(rs2CdbHit, io.cdbIn.bits.data, dataArray(io.duIn.rs2PhysIdx))
  io.duOut.rs2Ready := readyArray(io.duIn.rs2PhysIdx) || rs2CdbHit

  // 3. CDB write back
  when(io.cdbIn.valid) {
    val pIdx = io.cdbIn.bits.physIdx
    when(pIdx =/= 0.U) { // protect x0
      dataArray(pIdx)  := io.cdbIn.bits.data
      readyArray(pIdx) := true.B
      printf("[RF] write data. physIdx: %d, data: %d\n", pIdx, io.cdbIn.bits.data)
    }
  }

  // 4. DU dispatch
  when(io.duIn.allocEn) {
    val aIdx = io.duIn.allocPhysIdx
    when(aIdx =/= 0.U) {
      readyArray(aIdx) := false.B
    }
  }

  io.valueQuery := dataArray(io.physIdxQuery)

  // protect x0
  dataArray(0)  := 0.U
  readyArray(0) := true.B
}