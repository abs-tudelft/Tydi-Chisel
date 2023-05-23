import chisel3._
import chisel3.util.log2Ceil
import chisel3.experimental.dataview._
import chisel3.internal.firrtl.Width
import chisel3.util.Decoupled
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

class Element() {
  val width: Int = 0
}

class Null() extends Element {}
class Group() extends Element {}
class Union() extends Element {}
class BitsEl(override val width: Int) extends Element {}

class PhysicalStream(val e: Element, val n: Int, val d: Int, val c: Int, val u: Element) extends Bundle {
  require(n >= 1)
  require(1 <= c && c <= 7)

  /** Indicates that the producer has valid data ready
   *
   * @group Signals
   */
  val valid = Output(Bool())

  /** Indicates that the consumer is ready to accept the data this cycle
   *
   * @group Signals
   */
  val ready = Input(Bool())

  private val indexWidth = log2Ceil(n)

  val data = Output(Bits((e.width * n).W))
  val last = Output(UInt(d.W))
  val stai = Output(UInt(indexWidth.W))
  val endi = Output(UInt(indexWidth.W))
  val strb = Output(UInt(n.W))
}

class HelloWorldModuleOut extends Module {
  val io = IO(new PhysicalStream(new BitsEl(8), n=6, d=2, c=7, u=new Null()))
//  io :<= DontCare

  private val sendStr: String = "Hello "

  val helloInts: Seq[Int] = sendStr.map((char: Char) => char.toInt)
  println(s"helloInts are $helloInts")
  val helloInt: BigInt = sendStr.foldLeft(BigInt(0))((num: BigInt, char: Char) => (num << 8) + char.toInt)
  println(s"helloInt is $helloInt")
  io.data := helloInt.U
  io.valid := true.B
  io.strb := Integer.parseInt("111111", 2).U
  io.stai := 0.U
  io.endi := 5.U
  io.last := 0.U
}

class HelloWorldModuleIn extends Module {
  val io = IO(Flipped(new PhysicalStream(new BitsEl(8), n=6, d=2, c=7, u=new Null())))
  io :<= DontCare
  io.ready := DontCare
}

class TopLevelModule extends Module {
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
  io.out := helloWorldOut.io.data.asSInt
}

object HelloWorld extends App {
  private val firOpts: Array[String] = Array("-disable-opt", "-O=debug", "-disable-all-randomization", "-strip-debug-info"/*, "-preserve-values=all"*/)
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
