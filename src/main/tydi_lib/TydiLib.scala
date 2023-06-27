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

/**
 * Physical stream signal definitions.
 * @param e Element type
 * @param n Number of lanes
 * @param d Dimensionality
 * @param c Complexity
 * @param u User signals
 */
abstract class PhysicalStreamBase(private val e: TydiEl, val n: Int, val d: Int, val c: Int, private val u: Data) extends TydiEl {
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
  val user: Data

  val lastWidth: Int = if (c == 7) d * n else d
  val last: UInt = Output(UInt(lastWidth.W))
  val stai: UInt = Output(UInt(indexWidth.W))
  val endi: UInt = Output(UInt(indexWidth.W))
  val strb: UInt = Output(UInt(n.W))
}

/**
 * A physical stream as defined in the Tydi specification.
 * https://abs-tudelft.github.io/tydi/specification/physical.html
 * @param e Element type
 * @param n Number of lanes
 * @param d Dimensionality
 * @param c Complexity
 * @param u User signals
 */
class PhysicalStream(private val e: TydiEl, n: Int = 1, d: Int = 0, c: Int, private val u: Data = Null()) extends PhysicalStreamBase(e, n, d, c, u) {
  override val elWidth: Int = e.getDataElementsRec.map(_.getWidth).sum
  val userElWidth: Int = u.getWidth
  val data: UInt = Output(UInt((elWidth*n).W))
  val user: UInt = Output(UInt(userElWidth.W))

  // Stream mounting function
  def :=[Tel <: TydiEl, Tus <: Data](bundle: PhysicalStreamDetailed[Tel, Tus]): Unit = {
    // This could be done with a :<>= but I like being explicit here to catch possible errors.
    if (!bundle.r) {
      this.endi := bundle.endi
      this.stai := bundle.stai
      this.strb := bundle.strb
      this.last := bundle.last
      this.valid := bundle.valid
      bundle.ready := this.ready
      this.data := bundle.getDataConcat
      this.user := bundle.getUserConcat
    } else {
      bundle.endi := this.endi
      bundle.stai := this.stai
      bundle.strb := this.strb
      bundle.last := this.last
      bundle.valid := this.valid
      this.ready := bundle.ready
      // Connect data bitvector back to bundle
      bundle.getDataElementsRec.foldLeft(0)((i, dataField) => {
        val width = dataField.getWidth
        dataField := this.data(i+width-1, i)
        i + width
      })
      // Connect user bitvector back to bundle
      // Todo: Investigate if this is really necessary or if connecting as Data Bundle/Vector directly is fine,
      //  since user signals are unspecified by the standard.
      bundle.getUserElements.foldLeft(0)((i, userField) => {
        val width = userField.getWidth
        userField := this.user(i+width-1, i)
        i + width
      })
    }
  }
}

object PhysicalStream {
  def apply(e: TydiEl, n: Int = 1, d: Int = 0, c: Int, u: Data = Null()): PhysicalStream = new PhysicalStream(e, n, d, c, u)
}

/**
 * High level stream abstraction, closer to the logical stream idea.
 * @param e Element type
 * @param n Number of lanes
 * @param d Dimensionality
 * @param c Complexity
 * @param r Direction
 * @param u User signals
 * @tparam Tel Element type, must be a [[TydiEl]]. Can include other streams
 * @tparam Tus User type, can be any [[Data]] signal.
 */
class PhysicalStreamDetailed[Tel <: TydiEl, Tus <: Data](private val e: Tel, n: Int = 1, d: Int = 0, c: Int, var r: Boolean = false, private val u: Tus = Null()) extends PhysicalStreamBase(e, n, d, c, u) {
  val data: Vec[Tel] = Output(Vec(n, e))
  val user: Tus = Output(u)

  override def getDataConcat: UInt = data.map(_.getDataConcat).reduce(Cat(_, _))

  def getUserConcat: UInt = user.asUInt

  override def getDataElementsRec: Seq[Data] = data.flatMap(_.getDataElementsRec)

  def getUserElements: Seq[Data] = user match {
    case x: Bundle => x.getElements
    case x: Data => x :: Nil
  }

  def el: Tel = data(0)

  def flip: PhysicalStreamDetailed[Tel, Tus] = {
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
  def apply[Tel <: TydiEl, Tus <: Data](e: Tel, n: Int = 1, d: Int = 0, c: Int, r: Boolean = false, u: Tus = Null()): PhysicalStreamDetailed[Tel, Tus] = Wire(new PhysicalStreamDetailed(e, n, d, c, r, u))
}

object StringUtils {
  implicit class ExtraStringMethods(s: Data) {
    def dir = s.specifiedDirection
  }
}

class TydiModule extends Module {
  def mount[Tel <: TydiEl, Tus <: Data](bundle: PhysicalStreamDetailed[Tel, Data], io: PhysicalStream): Unit = {
    io := bundle
  }

  def reverseTranspile(): String = {
    val ports = getModulePorts.filter {
      case _: PhysicalStream => true
      case _ => false
    }
    val moduleName = this.name
    this.getModulePortsAndLocators()
    var str = s"streamlet ${moduleName}_interface {\n"
    for (elem <- ports) {
      val named = elem.toNamed
      val t2 = elem.toPrintable
      val t3 = elem.toString
      val pathName = elem.pathName
      val instanceName = elem.instanceName
      val direction = elem.specifiedDirection
      str += s"    ${instanceName} : ${moduleName}_${instanceName} out;\n"
    }
    str += s"}\n\nimpl ${moduleName} of ${moduleName}_interface {\n"
    for (elem <- ports) {
      val instanceName = elem.instanceName
      val target = elem.toAbsoluteTarget
      str += s"    self.${instanceName} => ...;\n"
    }
    str += "}\n"
    println(ports)
    str
  }
}
