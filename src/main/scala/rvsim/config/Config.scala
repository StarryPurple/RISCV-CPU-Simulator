package rvsim.config

import chisel3._
import chisel3.util._

// RISC-V 32
object Config {
  val TERMINATE_INSTR = 0xfe000fa3L // sb x0, -1(x0)

  val MEM_SIZE = 16 * 1024 // 16KB

  val XLEN = 32 // data and addr width
  val DATA_WIDTH = 32
  val INST_WIDTH = 32
  
  val MEM_LATENCY = 3
  
  val ROB_ENTRIES = 32
  val ROB_IDX_WIDTH = 5            // log2Ceil(ROB_ENTRIES)
  
  val RS_ENTRIES = 16
  val RS_IDX_WIDTH = 4             // log2Ceil(RS_ENTRIES)
  
  val LSB_ENTRIES = 16
  val LSB_IDX_WIDTH = 4            // log2Ceil(LSB_ENTRIES)
  
  // 寄存器文件参数
  val ARCH_REG_COUNT = 32          // RISC-V
  val PHYS_REG_COUNT = 64
  val REG_ID_WIDTH = 5             // log2Ceil(ARCH_REG_COUNT)
  val PHYS_REG_ID_WIDTH = 6        // log2Ceil(PHYS_REG_COUN
  def log2Ceil(x: Int): Int = scala.math.ceil(scala.math.log(x)/scala.math.log(2)).toInt
}