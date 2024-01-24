package nl.tudelft.tydi_chisel.examples.chisel

import chisel3._
import chisel3.util.Counter
import circt.stage.ChiselStage

class Blinky(freq: Int, startOn: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val led0 = Output(Bool())
  })
  // Blink LED every second using Chisel built-in util.Counter
  val led              = RegInit(startOn.B)
  val (_, counterWrap) = Counter(true.B, freq / 2)
  when(counterWrap) {
    led := ~led
  }
  io.led0 := led
}

object Blink extends App {
  println("Test123")

  // These lines generate the Verilog output
  println(
    ChiselStage
      .emitSystemVerilog(new Blinky(1000), firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info"))
  )
}
