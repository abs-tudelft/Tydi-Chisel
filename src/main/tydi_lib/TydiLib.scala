package tydi_lib

import chisel3._
import chisel3.util.{Cat, log2Ceil}
import chisel3.internal.firrtl.Width
import tydi_lib.ReverseTranspiler._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

trait TranspileExtend {
  /**
   * Adds this component's Tydi-lang representation to the map of definitions.
   * @param map Map with current Tydi-lang definitions
   * @return Map with component's Tydi-lang representation included
   */
  def transpile(map: mutable.LinkedHashMap[String, String]): mutable.LinkedHashMap[String, String]

  /**
   * Generate Tydi-lang code for this specific element.
   * @return Component in Tydi-lang representation
   */
  def tydiCode: String

  /**
   * Gets a unique representation of this element.
   * @return Unique string representation
   */
  def fingerprint: String
}

sealed trait TydiEl extends Bundle with TranspileExtend {
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

  def fingerprint: String = this.instanceName

  def transpile(map: mutable.LinkedHashMap[String, String]): mutable.LinkedHashMap[String, String] = {
    var m = map
    val s = fingerprint
    if (m.contains(s)) return m
    m += (fingerprint -> tydiCode)
    m
  }
}

sealed class Null extends TydiEl {
  // Don't do anything for transpilation. Null is a standard element.
  override def transpile(map: mutable.LinkedHashMap[String, String]): mutable.LinkedHashMap[String, String] = map

  override def tydiCode: String = s"${fingerprint} = Null;"

  override def fingerprint: String = "Null"
}

object Null {
  def apply(): Null = new Null
}

class Group() extends Bundle with TydiEl {
  def tydiCode: String = {
    var str = s"Group $fingerprint {\n"
    for ((elName, el) <- this.elements) {
      str += s"    $elName: ${el.fingerprint};\n"
    }
    str += "}"
    str
  }

  override def transpile(map: mutable.LinkedHashMap[String, String]): mutable.LinkedHashMap[String, String] = {
    var m = map
    // Add all group elements to the map
    for (el <- this.getElements) {
      m = el.transpile(m)
    }
    val s = fingerprint
    if (map.contains(s)) return m
    m += (fingerprint -> tydiCode)
    m
  }

  override def fingerprint: String = this.className
}

/**
 * A `Union` is similar to a [[Group]], but has an additional `tag` signal that defines which of the other signals is
 * relevant/valid.
 * @param n Number of items
 */
class Union(val n: Int) extends TydiEl {
  private val tagWidth = log2Ceil(n)
  val tag: UInt = UInt(tagWidth.W)

  def tydiCode: String = {
    var str = s"Union $fingerprint {\n"
    for ((elName, el) <- this.elements) {
      if (elName != "tag")
        str += s"    $elName: ${el.fingerprint};\n"
    }
    str += "}"
    str
  }

  override def transpile(map: mutable.LinkedHashMap[String, String]): mutable.LinkedHashMap[String, String] = {
    var m = map
    // Add all union elements to the map
    for ((elName, el) <- this.elements) {
      if (elName != "tag")
        m = el.transpile(m)
    }
    val s = fingerprint
    if (map.contains(s)) return m
    m += (fingerprint -> tydiCode)
    m
  }

  override def fingerprint: String = this.className

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

  override def tydiCode: String = value.tydiCode

  override def fingerprint: String = value.fingerprint

  override def transpile(map: mutable.LinkedHashMap[String, String]): mutable.LinkedHashMap[String, String] = value.transpile(map)
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


  def tydiCode: String = {
    val elName = e.fingerprint
    val usName = u.fingerprint
    u match {
      case _: Null => s"$fingerprint = Stream($elName, t=$n, d=$d, c=$c)"
      case _ => s"$fingerprint = Stream($elName, t=$n, d=$d, c=$c, u=$usName)"
    }
  }

  override def transpile(map: mutable.LinkedHashMap[String, String]): mutable.LinkedHashMap[String, String] = {
    var m = map
    m = e.transpile(m)
    val s = fingerprint
    if (m.contains(s)) return m
    m += (fingerprint -> tydiCode)
    m
  }

  override def fingerprint: String = {
    u match {
      case _: Null => s"${e.fingerprint}_stream"
      case _ => s"${e.fingerprint}_${u.fingerprint}_stream"
    }
  }
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
        // .asTypeOf cast is necessary to prevent incompatible type errors
        dataField := this.data(i + width - 1, i).asTypeOf(dataField)
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

  def :=(bundle: PhysicalStream): Unit = {
    // This could be done with a :<>= but I like being explicit here to catch possible errors.
    this.endi := bundle.endi
    this.stai := bundle.stai
    this.strb := bundle.strb
    this.last := bundle.last
    this.valid := bundle.valid
    bundle.ready := this.ready
    this.data := bundle.data
    this.user := bundle.user
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

  // Stream mounting function
  def :=[TBel <: TydiEl, TBus <: Data](bundle: PhysicalStreamDetailed[TBel, TBus]): Unit = {
    // This could be done with a :<>= but I like being explicit here to catch possible errors.
    if (bundle.r && !this.r) {
      this.endi := bundle.endi
      this.stai := bundle.stai
      this.strb := bundle.strb
      this.last := bundle.last
      this.valid := bundle.valid
      bundle.ready := this.ready
      (this.data: Data).waiveAll :<>= (bundle.data: Data).waiveAll
      (this.user: Data).waiveAll :<>= (bundle.user: Data).waiveAll
    } else {
      bundle.endi := this.endi
      bundle.stai := this.stai
      bundle.strb := this.strb
      bundle.last := this.last
      bundle.valid := this.valid
      this.ready := bundle.ready
      (bundle.data: Data).waiveAll :<>= (this.data: Data).waiveAll
      (bundle.user: Data).waiveAll :<>= (this.user: Data).waiveAll
    }
  }
}

object PhysicalStreamDetailed {
  def apply[Tel <: TydiEl, Tus <: Data](e: Tel, n: Int = 1, d: Int = 0, c: Int, r: Boolean = false, u: Tus = Null()): PhysicalStreamDetailed[Tel, Tus] = Wire(new PhysicalStreamDetailed(e, n, d, c, r, u))
}

class TydiModule extends Module with TranspileExtend {

  private val moduleList = ListBuffer[TydiModule]()

  def mount[Tel <: TydiEl, Tus <: Data](bundle: PhysicalStreamDetailed[Tel, Data], io: PhysicalStream): Unit = {
    io := bundle
  }

  def Module [T <: TydiModule](bc: => T): T = {
    val v = chisel3.Module.apply(bc)
    moduleList += v
    v
  }

  override def getModulePorts: Seq[Data] = super.getModulePorts

  override def transpile(_map: mutable.LinkedHashMap[String, String]): mutable.LinkedHashMap[String, String] = {
    var map = _map

    for (module <- moduleList) {
      map = module.transpile(map)
    }

    val ports: Seq[PhysicalStream] = getModulePorts.filter {
      case _: PhysicalStream => true
      case _ => false
    }.map(_.asInstanceOf[PhysicalStream])

    for (elem <- ports) {
      map = elem.transpile(map)
    }
    val moduleName = this.name
    val streamletName = s"${moduleName}_interface"

    var str = s"streamlet $streamletName {\n"
    for (elem <- ports) {
      val instanceName = elem.instanceName
      val containsOut = instanceName.toLowerCase.contains("out")
      val containsIn = instanceName.toLowerCase.contains("in")
      val dirWord = if (containsOut) "out" else if (containsIn) "in" else "unknown"
      str += s"    $instanceName : ${elem.fingerprint} $dirWord;\n"
    }
    str += "}"
    map += (streamletName -> str)

    str = s"impl $moduleName of $streamletName {\n"
    for (port <- ports) {
      str += s"    self.${port.instanceName} => ...;\n"
    }
    for (module <- moduleList) {
      val instanceName = module.instanceName
      str += s"    instance $instanceName(${module.name});\n"
      val modulePorts: Seq[PhysicalStream] = module.getModulePorts.filter {
        case _: PhysicalStream => true
        case _ => false
      }.map(_.asInstanceOf[PhysicalStream])
      for (port <- modulePorts) {
        str += s"    $instanceName.${port.instanceName} => ...;\n"
      }
    }
    str += "}"
    map += (moduleName -> str)
    map
  }

  override def tydiCode: String = {
    var map = mutable.LinkedHashMap[String, String]()
    map = transpile(map)

    val str = map.values.mkString("\n\n")
    str
  }

  override def fingerprint: String = this.name
}
