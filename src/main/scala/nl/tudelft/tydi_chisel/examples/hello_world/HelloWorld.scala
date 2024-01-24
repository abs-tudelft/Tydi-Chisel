package nl.tudelft.tydi_chisel.examples.hello_world

import nl.tudelft.tydi_chisel._
import chisel3._
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}
import nl.tudelft.tydi_chisel.utils.ComplexityConverter

trait myTypes {
  def HelloWorldStreamType: PhysicalStreamDetailed[BitsEl, Null] =
    PhysicalStreamDetailed(BitsEl(8.W), n = 6, d = 2, c = 7, u = new Null())
}

class HelloWorldModuleOut extends Module with myTypes {
  val stream: PhysicalStreamDetailed[BitsEl, Null] = HelloWorldStreamType
  val io: PhysicalStream                           = stream.toPhysical

  private val sendStr: String = "Hello "

  val helloInts: Seq[Int] = sendStr.map((char: Char) => char.toInt)
  println(s"helloInts are $helloInts")
//  stream.data := helloInts.map(_.U)
  helloInts.map(_.U).zip(stream.data).foreach(x => x._2.value := x._1)
  stream.valid := true.B
  stream.strb  := Integer.parseInt("111111", 2).U
  stream.stai  := 0.U
  stream.endi  := 5.U
  stream.last  := 0.U
  stream.ready := DontCare
}

class HelloWorldModuleIn extends Module with myTypes {
  val stream: PhysicalStreamDetailed[BitsEl, Null] = HelloWorldStreamType.flip
  val io: PhysicalStream                           = stream.toPhysical
//  stream :<= DontCare
  stream.ready := DontCare
}

class TopLevelModule extends Module with myTypes {
  val io = IO(new Bundle {
    val in  = Input(UInt(64.W))
    val out = Output(SInt(128.W))
  })

  val helloWorldOut = Module(new HelloWorldModuleOut())
  val helloWorldIn  = Module(new HelloWorldModuleIn())

  val converter = Module(new ComplexityConverter(helloWorldOut.io, memSize = 20))
  converter.in    := helloWorldOut.io
  helloWorldIn.io := converter.out

  // Bi-directional connection
//  helloWorldIn.io := helloWorldOut.io
//  io.out := helloWorldOut.io.data.asSInt
  io.out := helloWorldOut.io.data.asUInt.asSInt
}

object HelloWorld extends App {
  println("Test123")

  println(emitCHIRRTL(new HelloWorldModuleOut()))
  println(emitSystemVerilog(new HelloWorldModuleOut(), firtoolOpts = firNoOptimizationOpts))

  println(emitCHIRRTL(new HelloWorldModuleIn()))
  println(emitSystemVerilog(new HelloWorldModuleIn(), firtoolOpts = firNoOptimizationOpts))

  println(emitCHIRRTL(new TopLevelModule()))

  // These lines generate the Verilog output
  private val noOptimizationVerilog: String =
    emitSystemVerilog(new TopLevelModule(), firtoolOpts = firNoOptimizationOpts)
  private val normalVerilog: String    = emitSystemVerilog(new TopLevelModule(), firtoolOpts = firNormalOpts)
  private val optimizedVerilog: String = emitSystemVerilog(new TopLevelModule(), firtoolOpts = firReleaseOpts)
//  println("\n\n\n")
//  println(noOptimizationVerilog)
  println("\n\n\n")
  println(normalVerilog)
  println("\n\n\n")
  println(optimizedVerilog)
  println("\n\n\n")
  println(s"No optimization: ${noOptimizationVerilog.lines.count()} lines")
  println(s"Debug optimization: ${normalVerilog.lines.count()} lines")
  println(s"Release optimization: ${optimizedVerilog.lines.count()} lines")

  println("Done")
}
