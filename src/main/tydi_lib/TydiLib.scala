package tydi_lib

import chisel3._
import chisel3.util.{Cat, log2Ceil}
import chisel3.internal.firrtl.Width

sealed trait TydiEl extends Bundle {
  val isStream: Boolean = false
  val elWidth: Int = 0
  def getWidth: Int
  def getElements: Seq[Data]

  /** Gets data elements without streams. I.e. filters out any `Element`s that are also streams */
  def getDataElements: Seq[Data] = getElements.filter(x => x match {
    case x: TydiEl => !x.isStream
    case _ => true
  })

  /** Recursive way of getting only the data elements of the stream. */
  def getDataElementsRec: Seq[Data] = {
    val els = getDataElements
    val mapped = els.flatMap(x => x match {
      case x: TydiEl => x.getDataElementsRec
      case x: Bundle => x.getElements
      case _ => x :: Nil
    })
    mapped
  }

  def getDataConcat: UInt = {
    // Filter out any `Element`s that are also streams.
    // `.asUInt` also does recursive action but we don't want sub-streams to be included.
    getDataElementsRec.map(_.asUInt).reduce((prev, new_) => Cat(prev, new_))
  }
}

sealed class Null extends TydiEl

object Null {
  def apply(): Null = new Null
}

class Group() extends Bundle with TydiEl

/**
 * A `Union` is similar to a [[Group]], but has an additional `tag` signal that defines which of the other signals is
 * relevant/valid.
 * @param n Number of items
 */
class Union(val n: Int) extends TydiEl {
  private val tagWidth = log2Ceil(n)
  val tag: UInt = UInt(tagWidth.W)

  /**
   * Generates code for an Enum object that contains `tag` value literals.
   * @return String with code.
   */
  def createEnum: String = {
    val name = this.getClass.getSimpleName
    var str: String = s"object ${name}Choices {\n"
    var i = 0
    for (elName <- elements.keys.toList.reverse) {
      if (elName != "tag") {
        str += s"  val ${elName}: UInt = ${i}.U(${tagWidth}.W)\n"
        i += 1
      }
    }
    str += "}"
    str
  }
}

class BitsEl(override val width: Width) extends TydiEl {
  val value: UInt = Bits(width)
}

object BitsEl {
  def apply(width: Width): BitsEl = new BitsEl(width)
}

abstract class PhysicalStreamBase(private val e: TydiEl, val n: Int, val d: Int, val c: Int, private val u: TydiEl) extends TydiEl {
  override val isStream: Boolean = true

  require(n >= 1)
  require(1 <= c && c <= 7)

  def elementType = e.cloneType

  /** Indicates that the producer has valid data ready
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

  val lastWidth: Int = if (c == 7) d * n else d
  val last: UInt = Output(UInt(lastWidth.W))
  val stai: UInt = Output(UInt(indexWidth.W))
  val endi: UInt = Output(UInt(indexWidth.W))
  val strb: UInt = Output(UInt(n.W))
}

class PhysicalStream(private val e: TydiEl, n: Int = 1, d: Int = 0, c: Int, private val u: TydiEl = Null()) extends PhysicalStreamBase(e, n, d, c, u) {
  override val elWidth: Int = e.getDataElementsRec.map(_.getWidth).sum
  val data: UInt = Output(UInt((elWidth*n).W))

  // Stream mounting function
  def :=[T <: TydiEl](bundle: PhysicalStreamDetailed[T]): Unit = {
    // This could be done with a :<>= but I like being explicit here to catch possible errors.
    if (!bundle.r) {
      this.endi := bundle.endi
      this.stai := bundle.stai
      this.strb := bundle.strb
      this.last := bundle.last
      this.valid := bundle.valid
      bundle.ready := this.ready
      this.data := bundle.getDataConcat
    } else {
      bundle.endi := this.endi
      bundle.stai := this.stai
      bundle.strb := this.strb
      bundle.last := this.last
      bundle.valid := this.valid
      this.ready := bundle.ready
      // Connect data bitvector back to bundle
      bundle.getDataElementsRec.foldLeft(0)((i, data) => {
        val width = data.getWidth
        data := this.data(i+width-1, i)
        i + width
      })
    }
  }
}

object PhysicalStream {
  def apply(e: TydiEl, n: Int = 1, d: Int = 0, c: Int, u: TydiEl = Null()): PhysicalStream = new PhysicalStream(e, n, d, c, u)
}

class PhysicalStreamDetailed[T <: TydiEl](private val e: T, n: Int = 1, d: Int = 0, c: Int, var r: Boolean = false, private val u: TydiEl = Null()) extends PhysicalStreamBase(e, n, d, c, u) {
  val data: Vec[T] = Output(Vec(n, e))

  override def getDataConcat: UInt = data.map(_.getDataConcat).reduce(Cat(_, _))

  override def getDataElementsRec: Seq[Data] = data.flatMap(_.getDataElementsRec)

  def el: T = data(0)

  def flip: PhysicalStreamDetailed[T] = {
    r = !r
    this
  }

  def toPhysical: PhysicalStream = {
    val flip = r
    val stream = new PhysicalStream(e, n, d, c, u)
    val io = IO(if (flip) Flipped(stream) else stream)
    io := this
    io
  }
}

object PhysicalStreamDetailed {
  def apply[T <: TydiEl](e: T, n: Int = 1, d: Int = 0, c: Int, r: Boolean = false, u: TydiEl = Null()): PhysicalStreamDetailed[T] = Wire(new PhysicalStreamDetailed(e, n, d, c, r, u))
}

class TydiModule extends Module {
  def mount[T <: TydiEl](bundle: PhysicalStreamDetailed[T], io: PhysicalStream): Unit = {
    io := bundle
  }
}
