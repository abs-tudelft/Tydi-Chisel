package main.timestamped_message

import chisel3._
import chisel3.util.{Cat, Decoupled, log2Ceil}
import chisel3.experimental.dataview._
import chisel3.internal.firrtl.Width
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

trait Element extends Bundle {
  val isStream: Boolean = false
  val elWidth: Int = 0
  def getWidth: Int
  def getElements: Seq[Data]

  /** Gets data elements without streams. I.e. filters out any `Element`s that are also streams */
  def getDataElements: Seq[Data] = getElements.filter(x => x match {
    case x: Element => !x.isStream
    case _ => true
  })

  /** Recursive way of getting only the data elements of the stream. */
  def getDataElementsRec: Seq[Data] = {
    val els = getDataElements
    val mapped = els.flatMap(x => x match {
      case x: Element => x.getDataElementsRec
      case x: Bundle => x.getElements
      case _ => x :: Nil
    })
    mapped
  }
}

class Null() extends Element

class Group() extends Bundle with Element

class Union() extends Element {
//  def getWidth: Int = {
//    elWidth
//  }
  val tag = UInt(0.W)
  val value = UInt(0.W)

//  def getElements: Seq[Data] = Seq[Data](UInt(elWidth.W))
}
class BitsEl(override val width: Width) extends Element {
  val value = Bits(width)
//  def getElements: Seq[Data] = Seq[Data](UInt(elWidth.W))
}

abstract class PhysicalStreamBase(private val e: Element, val n: Int, val d: Int, val c: Int, private val u: Element) extends Element {
  override val isStream: Boolean = true

  require(n >= 1)
  require(1 <= c && c <= 7)

  /** Indicates that the producer has valid data readyDu
   *
   * @group Signals
   */
  val valid: Bool = Output(Bool())

  /** Indicates that the consumer is ready to accept the data this cycle
   *
   * @group Signals
   */
  val ready: Bool = Input(Bool())

  private val indexWidth = log2Ceil(n)

  val data: Data

  val last: UInt = Output(UInt(d.W))
  val stai: UInt = Output(UInt(indexWidth.W))
  val endi: UInt = Output(UInt(indexWidth.W))
  val strb: UInt = Output(UInt(n.W))
}

class PhysicalStream(private val e: Element, n: Int = 1, d: Int = 0, c: Int, private val u: Element = new Null) extends PhysicalStreamBase(e, n, d, c, u) {
  require(n >= 1)
  require(1 <= c && c <= 7)

  val data: UInt = Output(UInt(e.getWidth.W))
}

class PhysicalStreamDetailed[T <: Element](private val e: T, n: Int = 1, d: Int = 0, c: Int, private val u: Element = new Null) extends PhysicalStreamBase(e, n, d, c, u) {
  require(n >= 1)
  require(1 <= c && c <= 7)

  val data: T = Output(e)
}

class NestedBundle extends Group {
  val a: UInt = UInt(8.W)
  val b: Bool = Bool()
}

class TimestampedMessageBundle extends Group {
  private val charWidth: Width = 8.W
  val time: UInt = UInt(64.W)
  val nested: Group = new NestedBundle
  /*// It seems anonymous classes don't work well
  val nested: Group = new Group {
    val a: UInt = UInt(8.W)
    val b: Bool = Bool()
  }*/
  val message = new PhysicalStreamDetailed(new BitsEl(charWidth), d = 1, c = 7)
}

class TimestampedMessageModuleOut extends Module {
  def mount[T <: Element](bundle: PhysicalStreamDetailed[T], io: PhysicalStream): Unit = {
    io.endi := bundle.endi
    io.stai := bundle.stai
    io.strb := bundle.strb
    io.last := bundle.last
    io.valid := bundle.valid
    bundle.ready := io.ready
    // Filter out any `Element`s that are also streams.
    // `.asUInt` also does recursive action but we don't want sub-streams to be included.
    var elements = bundle.data.getDataElementsRec
    io.data := elements.map(_.asUInt).reduce((prev, new_) => Cat(prev, new_))
  }

  private val timestampedMessageBundle = new TimestampedMessageBundle // Can also be inline
  // Create Tydi logical stream object
  val stream: PhysicalStreamDetailed[TimestampedMessageBundle] = Wire(new PhysicalStreamDetailed(timestampedMessageBundle, 1, c = 7))
  // Create and connect physical stream following standard with concatenated data bitvector
  val tydi_port_top: PhysicalStream = IO(new PhysicalStream(timestampedMessageBundle, 1, c = 7))
  val tydi_port_child: PhysicalStream = IO(new PhysicalStream(new BitsEl(8.W), 1, c = 7))
  mount(stream, tydi_port_top)
  mount(stream.data.message, tydi_port_child)


  // Assign values to logical stream group elements directly
  stream.data.time := System.currentTimeMillis().U
  stream.data.message.data.value := 'H'.U(8.W)

  // Assign some values to the other Tydi signals
  // We have 1 lane in this case
  stream.valid := true.B
  stream.strb := 1.U
  stream.stai := 0.U
  stream.endi := 1.U
  stream.last := 0.U

  stream.data.message.valid := true.B
  stream.data.message.strb := 1.U
  stream.data.message.stai := 0.U
  stream.data.message.endi := 1.U
  stream.data.message.last := 0.U
}

class TimestampedMessageModuleIn extends Module {
  val io1 = IO(Flipped(new PhysicalStream(new TimestampedMessageBundle, n=1, d=2, c=7, u=new Null())))
  val io2 = IO(Flipped(new PhysicalStream(new BitsEl(8.W), n=1, d=2, c=7, u=new Null())))
  io1 :<= DontCare
  io1.ready := DontCare
  io2 :<= DontCare
  io2.ready := DontCare
}

class TopLevelModule extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(64.W))
    val out = Output(SInt(128.W))
  })

  val timestampedMessageOut = Module(new TimestampedMessageModuleOut())
  val timestampedMessageIn = Module(new TimestampedMessageModuleIn())

  // Bi-directional connection
  timestampedMessageIn.io1 :<>= timestampedMessageOut.tydi_port_top
  timestampedMessageIn.io2 :<>= timestampedMessageOut.tydi_port_child
  io.out := timestampedMessageOut.tydi_port_top.data.asSInt
}

object TimestampedMessage extends App {
  private val firOpts: Array[String] = Array("-disable-opt", "-O=debug", "-disable-all-randomization", "-strip-debug-info"/*, "-preserve-values=all"*/)
  println("Test123")

  println(emitCHIRRTL(new TimestampedMessageModuleOut()))
  println(emitSystemVerilog(new TimestampedMessageModuleOut(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new TimestampedMessageModuleIn()))
  println(emitSystemVerilog(new TimestampedMessageModuleIn(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new TopLevelModule()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new TopLevelModule(), firtoolOpts = firOpts))

  println("Done")
}
