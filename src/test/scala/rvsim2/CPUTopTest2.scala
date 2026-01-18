import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import rvsim2.CPUTop
import chisel3._

class CPUTopSpec2 extends AnyFlatSpec with ChiselScalatestTester {
  "CPUTop" should "load hex from file and execute" in {
    test(new CPUTop).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      println("--- Start testing ---")
      // read Verilog Hex file
      val hexPath = "src/main/resources/program.hex"
      val source = scala.io.Source.fromFile(hexPath)
      val lines = source.getLines().toList
      source.close()

      var currentAddr = 0
      println("--- Loading Hex into MIU ---")

      for (line <- lines) {
        val trimmed = line.trim
        if (trimmed.nonEmpty) {
          if (trimmed.startsWith("@")) {
            // address @xxxxxxxx
            currentAddr = Integer.parseInt(trimmed.substring(1), 16)
          } else {
            // bytes flow
            val bytes = trimmed.split("\\s+").filter(_.nonEmpty)
            bytes.grouped(4).foreach { group =>
              if (group.length == 4) {
                val word = group.zipWithIndex.map { case (b, i) =>
                  BigInt(b, 16) << (i * 8)
                }.sum
                
                // load instr
                dut.io.loadPort.en.poke(true.B)
                dut.io.loadPort.addr.poke(currentAddr.U)
                dut.io.loadPort.data.poke(word.U)
                dut.clock.step(1) // allow load
                
                currentAddr += 4
              }
            }
          }
        }
      }

      dut.io.loadPort.en.poke(false.B)
      println(s"Load complete. Last Address: ${currentAddr.toHexString}")
      
      val maxCycles = 5000
      var cycles = 0
      var halted = false

      println("--- Simulation Started ---")

      while (!halted && cycles < maxCycles) {
        print("Cycle "); println(cycles);
        if (dut.io.terminated.peek().litToBoolean) {
          val result = dut.io.exitCode.peek().litValue
          println(s"\n[HALT] Terminated at cycle $cycles")
          println(s"Result (x10/a0): $result")
          halted = true
        }
        
        dut.clock.step(1)
        cycles += 1
      }

      if (!halted) {
        println("\n[ERROR] Simulation Timed Out!")
        println(s"Current x10 value: ${dut.io.exitCode.peek().litValue}")
      }
      println("\n--- Simulation Finished ---")
    }
  }
}