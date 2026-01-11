package pwm
import chisel3._

class PWM(val resolution: Int = 8) extends Module {
  val io = IO(new Bundle{
    val dutyCycle = Input(UInt(resolution.W))
    val pwmOut    = Output(Bool())
  })
  
  val counter = RegInit(0.U(resolution.W))

  when (counter === ((1 << resolution) - 1).U) {
    counter := 0.U
  } .otherwise {
    counter := counter + 1.U
  }

  io.pwmOut := counter < io.dutyCycle
}

object PWM extends App {
  import circt.stage.ChiselStage
  ChiselStage.emitSystemVerilogFile(
    new PWM(8),
    Array("--target-dir", "generated")
  )
}