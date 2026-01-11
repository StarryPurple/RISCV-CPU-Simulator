package pwm
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PWMTester extends AnyFlatSpec with ChiselScalatestTester {
  it should "generate correct waveform" in {
    test(new PWM(4)) { dut =>
      dut.io.dutyCycle.poke(7.U)
      var highCount = 0
      for (_ <- 0 until 16) {
        if (dut.io.pwmOut.peek().litToBoolean) {
          highCount += 1
        }
        dut.clock.step()
      }
      assert(highCount == 7)
    }
  }

  it should "handle zero duty cycle" in {
    test(new PWM(4)) { dut =>
      dut.io.dutyCycle.poke(0.U)
      dut.io.pwmOut.expect(false.B)
      dut.clock.step(10)
    }
  }

  it should "handle full duty cycle" in {
    test(new PWM(4)) { dut => 
      dut.io.dutyCycle.poke(15.U)
      dut.io.pwmOut.expect(true.B)
      dut.clock.step(10)
    }
  }
}