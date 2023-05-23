package main.rgb

import chisel3._
import chisel3.util.{Cat, Decoupled, log2Ceil}
import chisel3.experimental.dataview._
import chisel3.internal.firrtl.Width
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

trait Element {
  val elWidth: Int = 0
  def getWidth: Int
//  val data: UInt = Wire(UInt(0.W))
}

class Null() extends Element {
  def getWidth: Int = {
    elWidth
  }
}
class Group() extends Bundle with Element {
//  def getWidth: Width = {
//    width
//  }
}

class Union() extends Element {
  def getWidth: Int = {
    elWidth
  }
}
class BitsEl(override val elWidth: Int) extends Element {
  def getWidth: Int = {
    elWidth
  }
}

class PhysicalStream(val e: Element, val n: Int, val d: Int, val c: Int, val u: Element) extends Bundle {
  require(n >= 1)
  require(1 <= c && c <= 7)

  /** Indicates that the producer has valid data readyDu
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

  val data = Output(UInt(e.getWidth.W))
//  data := e.data
  val last = Output(UInt(d.W))
  val stai = Output(UInt(indexWidth.W))
  val endi = Output(UInt(indexWidth.W))
  val strb = Output(UInt(n.W))
}

class RgbBundle extends Group {
  private val channelWidth: Width = 8.W
  val r: UInt = UInt(channelWidth)
  val g: UInt = UInt(channelWidth)
  val b: UInt = UInt(channelWidth)

//  override private val data: UInt = Wire(UInt(24.W))
//  data := r ## g ## b
}

class RgbModuleOut extends Module {
  private val rgbBundle = new RgbBundle
  val io = IO(new PhysicalStream(rgbBundle, n=1, d=2, c=7, u=new Null()))
//  io :<= DontCare

  private val rgbVal:Seq[Int] = Seq(0, 166, 244)
  val rgbWire: RgbBundle = Wire(rgbBundle)
//  val bundleEls: Seq[UInt] = rgbWire.getElements.map(_.asUInt)
//  io.data := bundleEls.tail.foldLeft(bundleEls.head)((prior, next) => Cat(prior, next))
  io.data := rgbWire.getElements.map(_.asUInt).reduce(Cat(_, _))
  // Generates
  // io.data := rgbWire.r ## rgbWire.g ## rgbWire.b
  rgbWire.r := rgbVal(0).U
  rgbWire.g := rgbVal(1).U
  rgbWire.b := rgbVal(2).U

//  private var count = 0
//  for (el <- rgbStuff.getElements) {
//    io.data[]
//    count += el.getWidth
//  }

//  io.data :=

  io.valid := true.B
  io.strb := 1.U
  io.stai := 0.U
  io.endi := 1.U
  io.last := 0.U
}

class RgbModuleIn extends Module {
  val io = IO(Flipped(new PhysicalStream(new RgbBundle, n=1, d=2, c=7, u=new Null())))
  io :<= DontCare
  io.ready := DontCare
}

class TopLevelModule extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(64.W))
    val out = Output(SInt(128.W))
  })

  val rgbOut = Module(new RgbModuleOut())
  val rgbIn = Module(new RgbModuleIn())

  // Bi-directional connection
  rgbIn.io :<>= rgbOut.io
  io.out := rgbOut.io.data.asSInt
}

object Rgb extends App {
  private val firOpts: Array[String] = Array("-disable-opt", "-O=debug", "-disable-all-randomization", "-strip-debug-info"/*, "-preserve-values=all"*/)
  println("Test123")

  println(emitCHIRRTL(new RgbModuleOut()))
  println(emitSystemVerilog(new RgbModuleOut(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new RgbModuleIn()))
  println(emitSystemVerilog(new RgbModuleIn(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new TopLevelModule()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new TopLevelModule(), firtoolOpts = firOpts))

  println("Done")
}
