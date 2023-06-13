import tydi_lib._
import chisel3._
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

trait myTypes {
  def HelloWorldStreamType: PhysicalStreamDetailed[BitsEl] = PhysicalStreamDetailed(new BitsEl(8.W), n = 6, d = 2, c = 7, u = new Null())
}

class HelloWorldModuleOut extends Module with myTypes {
  val stream: PhysicalStreamDetailed[BitsEl] = HelloWorldStreamType
  val io: PhysicalStream = stream.toPhysical

  private val sendStr: String = "Hello "

  val helloInts: Seq[Int] = sendStr.map((char: Char) => char.toInt)
  println(s"helloInts are $helloInts")
//  stream.data := helloInts.map(_.U)
  helloInts.map(_.U).zip(stream.data).foreach(x => x._2.value := x._1)
  stream.valid := true.B
  stream.strb := Integer.parseInt("111111", 2).U
  stream.stai := 0.U
  stream.endi := 5.U
  stream.last := 0.U
  stream.ready := DontCare
}

class HelloWorldModuleIn extends Module with myTypes {
  val stream: PhysicalStreamDetailed[BitsEl] = HelloWorldStreamType.flip
  val io: PhysicalStream = stream.toPhysical
  stream :<= DontCare
  stream.ready := DontCare
}

class TopLevelModule extends Module with myTypes {
  val io = IO(new Bundle {
    val in = Input(UInt(64.W))
    val out = Output(SInt(128.W))
  })

  val helloWorldOut = Module(new HelloWorldModuleOut())
  val helloWorldIn = Module(new HelloWorldModuleIn())

  // Mono-directional "strong connect" operator
  /*helloWorldIn.io.data := helloWorldOut.io.data
  helloWorldIn.io.last := helloWorldOut.io.last
  helloWorldIn.io.stai := helloWorldOut.io.stai
  helloWorldIn.io.endi := helloWorldOut.io.strb
  helloWorldIn.io.valid := helloWorldOut.io.valid
  helloWorldOut.io.ready := helloWorldOut.io.ready*/

  // Bi-directional connection
  helloWorldIn.io :<>= helloWorldOut.io
//  io.out := helloWorldOut.io.data.asSInt
  io.out := helloWorldOut.io.data.asUInt.asSInt
}

object HelloWorld extends App {
  private val firOpts: Array[String] = Array("-disable-opt", /*"-O=debug", */"-disable-all-randomization", "-strip-debug-info"/*, "-preserve-values=all"*/)
  println("Test123")

  println(emitCHIRRTL(new HelloWorldModuleOut()))
  println(emitSystemVerilog(new HelloWorldModuleOut(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new HelloWorldModuleIn()))
  println(emitSystemVerilog(new HelloWorldModuleIn(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new TopLevelModule()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new TopLevelModule(), firtoolOpts = firOpts))

  println("Done")
}
