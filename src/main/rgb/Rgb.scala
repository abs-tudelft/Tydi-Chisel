package main.rgb

import chisel3._
import chisel3.util.{Cat, Decoupled, log2Ceil}
import chisel3.experimental.dataview._
import chisel3.internal.firrtl.Width
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

trait Element extends Bundle {
  val elWidth: Int = 0
  def getWidth: Int
  def getElements: Seq[Data]
//  val data: UInt = Wire(UInt(0.W))
}

class Null() extends Element {}

class Group() extends Bundle with Element {
//  def getWidth: Width = {
//    width
//  }
}

class Union() extends Element {
//  def getWidth: Int = {
//    elWidth
//  }
  val tag = UInt(0.W)
  val value = UInt(0.W)

//  def getElements: Seq[Data] = Seq[Data](UInt(elWidth.W))
}
class BitsEl(override val width: Width) extends Element {
//  def getWidth: Int = {
//    elWidth
//  }

  val value = Bits(width)

//  def getElements: Seq[Data] = Seq[Data](UInt(elWidth.W))
}

class PhysicalStream(private val e: Element, val n: Int, val d: Int, val c: Int, private val u: Element) extends Bundle {
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
  // data := 50.U // Cannot set data here because data is not yet IO'ified
//  e match {
//    case e: Group => data := e.getElements.map(_.asUInt).reduce(Cat(_, _))
//  }
  val last = Output(UInt(d.W))
  val stai = Output(UInt(indexWidth.W))
  val endi = Output(UInt(indexWidth.W))
  val strb = Output(UInt(n.W))
}

class TydiStream[T <: Element](val streamType: T = new Null,
             val throughput: Double = 1.0, val dimensionality: Int = 0, val complexity: Int,
             val user: Element = new Null, val keep: Boolean = false
            ) extends RawModule {

  val n: Int = throughput.ceil.toInt

  val io: PhysicalStream = IO(new PhysicalStream(streamType, n=n, d=dimensionality, c=complexity, u=user))
  def ioType: PhysicalStream = io.cloneType

  val data: T = IO(Input(streamType))

  val valid = IO(Input(Bool()))
  io.valid := valid
  val ready = IO(Output(Bool()))
  ready := io.ready
  val last = IO(Input(io.last.cloneType))
  io.last := last
  val stai = IO(Input(io.stai.cloneType))
  io.stai := stai
  val endi = IO(Input(io.endi.cloneType))
  io.endi := endi
  val strb = IO(Input(io.strb.cloneType))
  io.strb := strb

  // Auto concatenate all data elements
  io.data := data.getElements.map(_.asUInt).reduce(Cat(_, _))
}

object TydiStream {
  def apply[T <: Element](streamType: T = new Null,
                          throughput: Double = 1.0, dimensionality: Int = 0, complexity: Int,
                          user: Element = new Null, keep: Boolean = false): TydiStream[T] =
    Module(new TydiStream(streamType, throughput, dimensionality, complexity, user, keep))
}

class RgbBundle extends Group {
  private val channelWidth: Width = 8.W
  val r: UInt = UInt(channelWidth)
  val g: UInt = UInt(channelWidth)
  val b: UInt = UInt(channelWidth)
}

class RgbModuleOut extends Module {
  private val rgbBundle = new RgbBundle // Can also be inline
  // Create Tydi logical stream object
  val stream: TydiStream[RgbBundle] = TydiStream(rgbBundle, 1, complexity = 7)
  // Create and connect physical stream following standard with concatenated data bitvector
  val tydi_port: PhysicalStream = IO(stream.ioType)
  tydi_port :<>= stream.io

  // Assign values to logical stream group elements directly
  private val rgbVal:Seq[Int] = Seq(0, 166, 244)  // TU Delft blue
  stream.data.r := rgbVal(0).U
  stream.data.g := rgbVal(1).U
  stream.data.b := rgbVal(2).U

  // Assign some values to the other Tydi signals
  // We have 1 lane in this case
  stream.valid := true.B
  stream.strb := 1.U
  stream.stai := 0.U
  stream.endi := 1.U
  stream.last := 0.U
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
  rgbIn.io :<>= rgbOut.tydi_port
  io.out := rgbOut.tydi_port.data.asSInt
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
