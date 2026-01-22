import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import chisel3._

class CPUTopSpec3 extends AnyFlatSpec with ChiselScalatestTester {
  "CPUTop" should "load hex from file and execute" in {
    test(new rvsim3.CentralProcessingUnit).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      println("--- Start testing ---")
      
      // Read Verilog Hex File
      val hexPath = "src/main/resources/program.hex"
      val source = scala.io.Source.fromFile(hexPath)
      val lines = source.getLines().toList
      source.close()

      var currentAddr = 0
      println("--- Loading Hex into RAM via PreloadBus ---")

      for (line <- lines) {
        val trimmed = line.trim
        if (trimmed.nonEmpty) {
          if (trimmed.startsWith("@")) {
            // @xxxxxxxx
            currentAddr = Integer.parseInt(trimmed.substring(1), 16)
          } else {
            val bytes = trimmed.split("\\s+").filter(_.nonEmpty)
            bytes.grouped(4).foreach { group =>
              if (group.length == 4) {
                val word = group.zipWithIndex.map { case (b, i) =>
                  BigInt(b, 16) << (i * 8)
                }.sum
                
                dut.io.preload.en.poke(true.B)
                dut.io.preload.addr.poke(currentAddr.U)
                dut.io.preload.data.poke(word.U)
                dut.clock.step(1)

                println("[Preload] Load instr ", word, " at addr ", currentAddr)
                
                currentAddr += 4
              }
            }
          }
        }
      }

      dut.io.preload.en.poke(false.B)
      println(s"Load complete. Last Address: ${currentAddr.toHexString}")
      
      val maxCycles = 1000
      var cycles = 0
      var halted = false

      println("--- Simulation Started ---")

      while (!halted && cycles < maxCycles) {
        if (dut.io.isTerminated.peek().litToBoolean) {
          val result = dut.io.returnVal.peek().litValue
          println(s"\n[HALT] Terminated at cycle $cycles")
          println(s"Result (x10/a0): $result")
          halted = true
        } else {
          println(s"\nCycle $cycles...")
          dut.clock.step(1)
          cycles += 1
        }
      }

      if (!halted) {
        println("\n[ERROR] Simulation Timed Out!")
        println(s"Current x10 value: ${dut.io.returnVal.peek().litValue}")
      }
      println("\n--- Simulation Finished ---")
    }
  }
  
}