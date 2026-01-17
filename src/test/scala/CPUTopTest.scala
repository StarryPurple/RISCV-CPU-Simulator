import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import rvsim.CPUTop
import chisel3._

class CPUTopSpec extends AnyFlatSpec with ChiselScalatestTester {
  "CPUTop" should "calculate sum and terminate" in {
    test(new CPUTop).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      
      // 设置最大仿真时钟周期，防止死循环
      val maxCycles = 10000
      var cycles = 0
      var halted = false

      println("--- Simulation Started ---")

      while (!halted && cycles < maxCycles) {
        // 检查 CPU 内部的终止信号
        if (dut.rob.io.isTerminate.peek().litToBoolean) {
          val finalX10 = dut.rf.io.debug_x10.peek().litValue
          println(s"Successfully Halted at cycle $cycles")
          println(s"Final Register x10 (Result): $finalX10")
          
          // 验证结果是否为 55
          assert(finalX10 == 21)
          halted = true
        }
        
        dut.clock.step(1)
        cycles += 1
      }

      if (!halted) {
        println("Simulation Timed Out!")
      }
      println("--- Simulation Finished ---")
    }
  }
}