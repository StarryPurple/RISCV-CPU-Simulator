package rvsim.config

import chisel3._
import chisel3.util._

// RISC-V 32
object Config {
  val XLEN = 32 // data and addr width
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
  val PHYS_REG_ID_WIDTH = 6        // log2Ceil(PHYS_REG_COUNT)
  
  val EU_COUNT = 1
  val EU_LATENCY = 1
  val EU_MUL_LATENCY = 3
  val EU_DIV_LATENCY = 10
  val EU_MEM_LATENCY = 1
  
  val CDB_COUNT = 1
  val CDB_TAG_WIDTH = 6
  
  val ICACHE_SIZE = 1024
  val DCACHE_SIZE = 1024
  val CACHE_LINE_SIZE = 64
  
  val PREDICTOR_TYPE = "BHT"
  val BHT_ENTRIES = 256
  val BTB_ENTRIES = 128
  
  val DEBUG_LEVEL = 1
  val DEBUG_VERBOSE = false
  val DEBUG_TRACE_ENABLE = true
  
  def log2Ceil(x: Int): Int = scala.math.ceil(scala.math.log(x)/scala.math.log(2)).toInt
}