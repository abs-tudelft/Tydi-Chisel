package pipeline

import tydi_lib._
import chisel3._
import chiseltest.RawTester.test
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

class NumberGroup extends Group {
  val time: UInt = UInt(64.W)
  val value: SInt = SInt(64.W)
}

class NumberModuleOut extends TydiModule {
  private val timestampedMessageBundle = new NumberGroup // Can also be inline

  // Create Tydi logical stream object
  val stream: PhysicalStreamDetailed[NumberGroup, Null] = PhysicalStreamDetailed(timestampedMessageBundle, 1, c = 7)

  // Create and connect physical streams following standard with concatenated data bitvector
  val tydi_port_top: PhysicalStream = stream.toPhysical

  // → Assign values to logical stream group elements directly
  stream.el.time := System.currentTimeMillis().U
  stream.el.value := -3457.U

  // → Assign some values to the other Tydi signals
  //   We have 1 lane in this case

  // Top stream
  stream.valid := true.B
  stream.strb := 1.U
  stream.stai := 0.U
  stream.endi := 1.U
  stream.last := 0.U
}

class NumberModuleIn extends TydiModule {
  val io1 = IO(Flipped(new PhysicalStream(new NumberGroup, n=1, d=2, c=7, u=new Null())))
  io1 :<= DontCare
  io1.ready := DontCare
}

class TopLevelModule extends TydiModule {
  val io = IO(new Bundle {
    val in = Input(UInt(64.W))
    val out = Output(SInt(128.W))
  })

  val numberModuleOut = Module(new NumberModuleOut())
  val numberModuleIn = Module(new NumberModuleIn())

  // Bi-directional connection
  numberModuleIn.io1 :<>= numberModuleOut.tydi_port_top
  io.out := numberModuleOut.tydi_port_top.data.asSInt
}

object PipelineExample extends App {
  println("Test123")

  test(new TopLevelModule()) { c =>
    println(c.tydiCode)
  }

  println(emitCHIRRTL(new NumberModuleOut()))
  println(emitSystemVerilog(new NumberModuleOut(), firtoolOpts = firNormalOpts))

  println(emitCHIRRTL(new NumberModuleIn()))
  println(emitSystemVerilog(new NumberModuleIn(), firtoolOpts = firNormalOpts))

  println(emitCHIRRTL(new TopLevelModule()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new TopLevelModule(), firtoolOpts = firNormalOpts))

  println("Done")
}
