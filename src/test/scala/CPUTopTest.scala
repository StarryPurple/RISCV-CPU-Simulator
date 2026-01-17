import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import rvsim.CPUTop
import chisel3._

class CPUTopSpec extends AnyFlatSpec with ChiselScalatestTester {
  "CPUTop" should "calculate sum and terminate" in {
    test(new CPUTop).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      val hexPath = "src/main/resources/program.hex"
      val source = scala.io.Source.fromFile(hexPath)
      val lines = source.getLines().toList
      source.close()

      var currentAddr = 0
      var byteGroup = Array.fill(4)("00")
      var count = 0

      for (line <- lines) {
        if (line.startsWith("@")) {
          currentAddr = Integer.parseInt(line.substring(1), 16)
        } else {
          val bytes = line.split(" ").filter(_.nonEmpty)
          for (b <- bytes) {
            byteGroup(count) = b
            count += 1
            if(count == 4) {
              val word = byteGroup.zipWithIndex.map { case (b, i) =>
                BigInt(b, 16) << (i * 8)
              }.sum
              dut.io.loadPort.en.poke(true.B)
              dut.io.loadPort.addr.poke(currentAddr.U)
              dut.io.loadPort.data.poke(word.U)
              println("Try to write instr")
              dut.clock.step(1)

              currentAddr += 4
              count = 0
            }
          }
        }
      }

      dut.io.loadPort.en.poke(false.B)
      println("Instr memory load complete")
      
      val maxCycles = 100
      var cycles = 0
      var halted = false

      println("--- Simulation Started ---")

      while (!halted && cycles < maxCycles) {
        print("New cycle: ")
        println(cycles)
        if (dut.io.isTerminate.peek().litToBoolean) {
          val finalX10 = dut.io.debug_x10.peek().litValue
          println(s"Successfully Halted at cycle $cycles")
          println(s"Final Register x10 (Result): $finalX10")
          
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