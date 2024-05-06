import chisel3._
import circt.stage.ChiselStage.emitCHIRRTL

class Example extends Module {
  val a, b, c  = IO(Input(Bool()))
  val d, e, f  = IO(Input(Bool()))
  val foo, bar = IO(Input(UInt(8.W)))
  val out      = IO(Output(UInt(8.W)))

  val myReg = RegInit(0.U(8.W))
  out := myReg

  when (a && b && c) {
    myReg := foo
  }
  when (d && e && f) {
    myReg := bar
  }
}


println(emitCHIRRTL(new Example))