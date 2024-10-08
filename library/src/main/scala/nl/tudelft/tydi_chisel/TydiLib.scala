package nl.tudelft.tydi_chisel

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import chisel3._
import chisel3.experimental.{BaseModule, ExtModule}
import chisel3.util.{log2Ceil, Cat}
import nl.tudelft.tydi_chisel.ReverseTranspiler._
import nl.tudelft.tydi_chisel.utils.ComplexityConverter

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
  val elWidth: Int      = 0
  def getWidth: Int
  def getElements: Seq[Data]

  /** Gets data elements without streams. I.e. filters out any `Element`s that are also streams */
  def getDataElements: Seq[Data] = getElements.filter(x =>
    x match {
      case x: TydiEl => !x.isStream
      case _         => true
    }
  )

  /** Gets stream elements. I.e. filters out any `Element`s that are data */
  def getStreamElements: Seq[PhysicalStreamDetailed[_, _]] = getElements.collect {
    case x: PhysicalStreamDetailed[_, _] => x
  }

  /** Recursive way of getting only the data elements of the stream. */
  def getDataElementsRec: Seq[Data] = {
    val els = getDataElements
    val mapped = els.flatMap(x =>
      x match {
        case x: TydiEl => x.getDataElementsRec
        case x: Bundle => x.getElements
        case _         => x :: Nil
      }
    )
    mapped
  }

  /** Recursive way of getting only the sub-stream elements of the stream. */
  def getSubStreamsRec: Seq[PhysicalStreamDetailed[_, _]] = {
    val els = getStreamElements
    val mapped = els.flatMap(x =>
      x match {
        case x: TydiEl => x.getSubStreamsRec
        case _         => x :: Nil
      }
    )
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
  val tag: UInt        = UInt(tagWidth.W)

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
    val name        = this.getClass.getSimpleName
    var str: String = s"object ${name}Choices {\n"
    var i           = 0
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

  override def transpile(map: mutable.LinkedHashMap[String, String]): mutable.LinkedHashMap[String, String] =
    value.transpile(map)
}

object BitsEl {
  def apply(width: Width): BitsEl = new BitsEl(width)
}

object CompatCheck extends Enumeration {
  val Params, Strict = Value
}

object CompatCheckResult extends Enumeration {
  val Warning, Error = Value
}

final case class TydiStreamCompatException(private val message: String = "", private val cause: Throwable = None.orNull)
      extends Exception(message, cause)

/**
 * Physical stream signal definitions.
 * @param e Element type
 * @param n Number of lanes
 * @param d Dimensionality
 * @param c Complexity
 * @param u User signals
 */
sealed abstract class PhysicalStreamBase(private val e: TydiEl, val n: Int, val d: Int, val c: Int, private val u: Data)
      extends TydiEl {
  override val isStream: Boolean = true
  override val elWidth: Int      = e.getDataElementsRec.map(_.getWidth).sum
  val userElWidth: Int           = u.getWidth

  require(n >= 1)
  require(1 <= c && c <= 8)

  def elementType = e.cloneType

  def getDataType: TydiEl = e
  def getUserType: Data   = u

  /**
   * Indicates that the producer has valid data ready.<br>
   * [C&lt;3] valid may only be released when lane `N−1` of the [[last]] signal in the acknowledged transfer is nonzero.<br>
   * [C&lt;2] valid may only be released when lane `N−1` of the [[last]] signal in the acknowledged transfer is all ones.<br>
   * https://abs-tudelft.github.io/tydi/specification/physical.html#valid-signal-description
   *
   * @group Signals
   */
  val valid: Bool = Output(Bool())

  /**
   * Indicates that the consumer is ready to accept the data this cycle.<br>
   * A transfer is considered "handshaked" when both [[valid]] and [[ready]] are asserted during the active clock edge of the clock domain common to the source and the sink.<br>
   * https://abs-tudelft.github.io/tydi/specification/physical.html#ready-signal-description
   * @group Signals
   */
  val ready: Bool = Input(Bool())

  private val indexWidth = log2Ceil(n)

  /**
   * Data signal of [[n]] lanes, each carrying data elements ([[e]]).<br>
   * A lane is active if
   * <ul>
   *   <li>bit `i` of [[strb]] is asserted</li>
   *   <li>the unsigned integer interpretation of [[endi]] &ge; `i`</li>
   *   <li>the unsigned integer interpretation of [[stai]] &le; `i`</li>
   * </ul>
   * https://abs-tudelft.github.io/tydi/specification/physical.html#data-signal-description
   * @group Signals
   */
  val data: Data

  /**
   * User signal of interface. Can be anything. Still, the state of the [[user]] signal is not significant when [[valid]] is not asserted.<br>
   * https://abs-tudelft.github.io/tydi/specification/physical.html#user-signal-description
   */
  val user: Data

  val lastWidth: Int = d * n

  /**
   * Last signal for signalling the end of nested sequences. Usage is highly dependent on complexity!<br>
   * The state of the [[last]] signal is significant only while [[valid]] is asserted. It is thus <i>not</i> controlled by which data lanes are active. <br>
   * [C&lt;8] All last bits for lanes `0` to `N−2` inclusive must be driven low by the source, and may be ignored by the sink.<br>
   * [C&lt;4] It is illegal to assert a [[last]] bit for dimension `j` without also asserting the last bits for dimensions `j′`&lt;`j` in the same lane.<br>
   * [C&lt;4] It is illegal to assert the [[last]] bit for dimension `0` when the respective data lane is inactive, except for empty sequences.<br>
   * https://abs-tudelft.github.io/tydi/specification/physical.html#last-signal-description
   * @group Signals
   */
  val last: Data

  /**
   * Lane validity start index signal for turning lanes on and off.<br>
   * The state of the [[stai]] signal is significant only while [[valid]] is asserted.<br>
   * [C&lt;6] [[stai]] must always be driven to `0` by the source, and may be ignored by the sink.<br>
   * https://abs-tudelft.github.io/tydi/specification/physical.html#stai-signal-description
   * @group Signals
   */
  val stai: UInt = Output(UInt(indexWidth.W))

  /**
   * Lane validity end index signal for turning lanes on and off.<br>
   * The state of the [[endi]] signal is significant only while [[valid]] is asserted.<br>
   * [C&lt;5] [[endi]] must be driven to `N−1` by the source when last is zero, and may be ignored by the sink in this case.<br>
   * https://abs-tudelft.github.io/tydi/specification/physical.html#endi-signal-description
   * @group Signals
   */
  val endi: UInt = Output(UInt(indexWidth.W))

  /**
   * Strobe signal for turning lanes on and off.<br>
   * [C&lt;8] All [[strb]] bits must be driven to the same value by the source. The sink only needs to interpret one of the bits.<br>
   * https://abs-tudelft.github.io/tydi/specification/physical.html#strb-signal-description
   * @group Signals
   */
  val strb: UInt = Output(UInt(n.W))

  /**
   * Creates a [[UInt]] bitmask for lane validity based on [[stai]] and [[endi]].
   * @return Bitmask based on [[stai]] and [[endi]]
   */
  def indexMask: UInt = {
    // Cannot use expression directly because the width is not inferred correctly
    val _indexMask: UInt = Wire(UInt(n.W))
    _indexMask := ((1.U << (endi - stai + 1.U(n.W))) - 1.U) << stai
    _indexMask
  }

  /**
   * Returns lane validity based on the [[strb]], [[stai]], and [[endi]] signals.
   */
  def laneValidity: UInt = (strb & indexMask)

  /**
   * Returns lane validity based on the [[strb]], [[stai]], and [[endi]] signals.
   */
  def laneValidityVec: Vec[Bool] = VecInit(laneValidity.asBools)

  /** [[strb]] signal as a boolean vector */
  def strbVec: Vec[Bool] = VecInit(strb.asBools)

  protected def printWarning(message: String): Unit = {
    // ANSI escape codes for bold and orange text
    val bold   = "\u001b[1m"
    val orange = "\u001b[38;5;214m"
    val reset  = "\u001b[0m"

    // Print the formatted message
    Console.err.println(s"$bold$orange$message$reset")
  }

  protected def reportProblem(problemStr: String, compatCheckResult: CompatCheckResult.Value): Unit = {
    val checkResult = nl.tudelft.tydi_chisel.compatCheckResult match {
      case None       => compatCheckResult
      case x: Some[_] => x.get
    }

    checkResult match {
      case CompatCheckResult.Error => throw TydiStreamCompatException(problemStr)
      case CompatCheckResult.Warning => {
        val stackTrace = new Exception().getStackTrace.slice(2, 8).mkString("\nTrace:\n\t", "\n\t", "\n")
        printWarning(problemStr + stackTrace)
      }
    }
  }

  /**
   * Check if the parameters of a source and sink stream match.
   * @param toConnect Source stream to drive this stream with.
   */
  def paramCheck(toConnect: PhysicalStreamBase, compatCheckResult: CompatCheckResult.Value): Unit = {
    // Number of lanes should be the same
    if (toConnect.n != this.n) {
      reportProblem(
        s"Number of lanes between source and sink is not equal.\n\t- ${this} has n=${this.n}\n\t- ${toConnect} has n=${toConnect.n}",
        compatCheckResult
      )
    }
    // Dimensionality should be the same
    if (toConnect.d != this.d) {
      reportProblem(
        s"Dimensionality of source and sink is not equal.\n\t- ${this} has d=${this.d}\n\t- ${toConnect} has d=${toConnect.d}",
        compatCheckResult
      )
    }
    // Sink C >= source C for compatibility
    if (toConnect.c > this.c) {
      reportProblem(
        s"Complexity of source stream > sink.\n\t- ${this} has c=${this.c}\n\t- ${toConnect} has c=${toConnect.c}",
        compatCheckResult
      )
    }
  }

  def elementCheck(toConnect: PhysicalStreamBase, compatCheckResult: CompatCheckResult.Value): Unit = {
    if (this.elWidth != toConnect.elWidth) {
      reportProblem(
        s"Size of stream elements is not equal.\n\t- ${this} has |e|=${this.elWidth}\n\t- ${toConnect} has |e|=${toConnect.elWidth}",
        compatCheckResult
      )
    }
    if (this.userElWidth != toConnect.userElWidth) {
      reportProblem(
        s"Size of stream elements is not equal.\n\t- ${this} has |u|=${this.userElWidth}\n\t- ${toConnect} has |u|=${toConnect.userElWidth}",
        compatCheckResult
      )
    }
  }

  def elementCheckTyped(
    toConnect: PhysicalStreamBase,
    typeCheck: CompatCheck.Value,
    compatCheckResult: CompatCheckResult.Value
  ): Unit = {
    val check = nl.tudelft.tydi_chisel.compatCheck match {
      case None       => typeCheck
      case x: Some[_] => x.get
    }

    if (check == CompatCheck.Strict) {
      if (this.getDataType.getClass != toConnect.getDataType.getClass) {
        reportProblem(
          s"Type of stream elements is not equal.\n\t- ${this} has e=${this.getDataType.getClass}, |e|=${this.elWidth}\n\t- ${toConnect} has e=${toConnect.getDataType.getClass}, |e|=${toConnect.elWidth}",
          compatCheckResult
        )
      }
      if (this.getUserType.getClass != toConnect.getUserType.getClass) {
        reportProblem(
          s"Type of user elements is not equal.\n\t- ${this} has u=${this.getUserType.getClass}, |u|=${this.userElWidth}\n\t- ${toConnect} has u=${toConnect.getUserType.getClass}, |u|=${toConnect.userElWidth}",
          compatCheckResult
        )
      }
    } else {
      elementCheck(toConnect, compatCheckResult)
    }
  }

  /**
   * Meta-connect function. Connects all metadata signals but not the data or user signals.
   * @param bundle Source stream to drive this stream with.
   * @param compatCheckResult Whether to report stream compatibility issues as errors or warnings.
   */
  def :@=(
    bundle: PhysicalStreamBase
  )(implicit compatCheckResult: CompatCheckResult.Value = CompatCheckResult.Error): Unit = {
    paramCheck(bundle, compatCheckResult)
    // This could be done with a :<>= but I like being explicit here to catch possible errors.
    this.endi    := bundle.endi
    this.stai    := bundle.stai
    this.strb    := bundle.strb
    this.valid   := bundle.valid
    bundle.ready := this.ready

    if (this.d > 0) {
      (this.last, bundle.last) match {
        case (_: UInt, _: UInt) | (_: Vec[_], _: Vec[_]) => this.last := bundle.last
        case (_: UInt, _: Vec[_])                        => this.last := bundle.last.asUInt
        case (thisLast: Vec[_], bundleLast: UInt) =>
          for ((lastLane, i) <- thisLast.zipWithIndex) {
            // Assign a slice of the bitvector to the respective lane in the vector
            lastLane := bundleLast((i + 1) * d - 1, i * d)
          }
      }
    } else {
      this.last   := DontCare
      bundle.last := DontCare
    }
  }

  /**
   * Shortcut for stream mounting with weak type check.
   * @param bundle Source stream to drive this stream with.
   * @param errorReporting Whether to report stream compatibility issues as errors or warnings.
   */
  def :~=(
    bundle: PhysicalStreamBase
  )(implicit errorReporting: CompatCheckResult.Value = CompatCheckResult.Error): Unit = {
    implicit val errorReporting: CompatCheck.Value = CompatCheck.Params
    this := bundle
  }

  /**
   * Stream mounting function.
   * @param bundle Source stream to drive this stream with.
   * @param errorReporting Whether to report stream compatibility issues as errors or warnings.
   * @param typeCheck Whether to conduct a strong or weak type check. A weak type check only verifies the number of bits.
   */
  def :=(bundle: PhysicalStreamBase)(implicit
    typeCheck: CompatCheck.Value = CompatCheck.Strict,
    errorReporting: CompatCheckResult.Value = CompatCheckResult.Error
  ): Unit = {
    this :@= bundle                                      // Connect meta signals and check parameters
    elementCheckTyped(bundle, typeCheck, errorReporting) // Check data types
    // Call the right connect method
    (this, bundle) match {
      case (x: PhysicalStream, y: PhysicalStream)               => x.connectSimple(y, typeCheck, errorReporting)
      case (x: PhysicalStream, y: PhysicalStreamDetailed[_, _]) => x.connectDetailed(y, typeCheck, errorReporting)
      case (x: PhysicalStreamDetailed[_, _], y: PhysicalStream) => x.connectSimple(y, typeCheck, errorReporting)
      case (x: PhysicalStreamDetailed[_, _], y: PhysicalStreamDetailed[_, _]) =>
        x.connectDetailed(y, typeCheck, errorReporting)
      case _ => throw new Exception("Could not determine data connection method.")
    }
  }

  def tydiCode: String = {
    val elName = e.fingerprint
    val usName = u.fingerprint
    u match {
      case _: Null => s"$fingerprint = Stream($elName, t=$n, d=$d, c=$c);"
      case _       => s"$fingerprint = Stream($elName, t=$n, d=$d, c=$c, u=$usName);"
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
      case _       => s"${e.fingerprint}_${u.fingerprint}_stream"
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
class PhysicalStream(private val e: TydiEl, n: Int = 1, d: Int = 1, c: Int, private val u: Data = Null())
      extends PhysicalStreamBase(e, n, d, c, u) {
  val data: UInt = Output(UInt((elWidth * n).W))
  val user: UInt = Output(UInt(userElWidth.W))
  val last: UInt = Output(UInt(lastWidth.W))

  def lastVec: Vec[UInt] = {
    if (d > 0)
      VecInit.tabulate(n)(i => last((i + 1) * d - 1, i * d))
    else
      VecInit.tabulate(n)(i => 0.U(0.W))
  }

  /**
   * Stream mounting function.
   * @param bundle Source stream to drive this stream with.
   * @tparam Tel Element signal type.
   * @tparam Tus User signal type.
   */
  private[tydi_chisel] def connectDetailed[Tel <: TydiEl, Tus <: Data](
    bundle: PhysicalStreamDetailed[Tel, Tus],
    typeCheck: CompatCheck.Value = CompatCheck.Strict,
    errorReporting: CompatCheckResult.Value
  ): Unit = {
    if (elWidth > 0) {
      this.data := bundle.getDataConcat
    } else {
      this.data := DontCare
    }
    if (userElWidth > 0) {
      this.user := bundle.getUserConcat
    } else {
      this.user := DontCare
    }
  }

  /**
   * Stream mounting function.
   * @param bundle Source stream to drive this stream with.
   */
  private[tydi_chisel] def connectSimple(
    bundle: PhysicalStream,
    typeCheck: CompatCheck.Value,
    errorReporting: CompatCheckResult.Value
  ): Unit = {
    this.data := bundle.data
    this.user := bundle.user
  }

  def processWith[T <: SubProcessorSignalDef](module: => T)(implicit parentModule: TydiModuleMixin): PhysicalStream = {
    val processingModule = parentModule.Module(module)
    processingModule.in := this
    processingModule.out
  }

  def processWithMod[T <: SubProcessorSignalDef](module: T): PhysicalStream = {
    module.in := this
    module.out
  }

  def convert(memSize: Int)(implicit parentModule: TydiModuleMixin): PhysicalStream = {
    val processingModule = parentModule.Module(new ComplexityConverter(this, memSize))
    processingModule.in := this
    processingModule.out
  }

  def duplicate(k: Int)(implicit parentModule: TydiModuleMixin): Vec[PhysicalStream] = {
    val processingModule = parentModule.Module(new StreamDuplicator(k, this))
    processingModule.in := this
    processingModule.out
  }
}

object PhysicalStream {
  def apply(e: TydiEl, n: Int = 1, d: Int = 1, c: Int, u: Data = Null()): PhysicalStream =
    new PhysicalStream(e, n, d, c, u)
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
class PhysicalStreamDetailed[Tel <: TydiEl, Tus <: Data](
  private val e: Tel,
  n: Int = 1,
  d: Int = 1,
  c: Int,
  var r: Boolean = false,
  private val u: Tus = Null()
) extends PhysicalStreamBase(e, n, d, c, u) {
  val data: Vec[Tel]                   = Output(Vec(n, e))
  val user: Tus                        = Output(u)
  val last: Vec[UInt]                  = Output(Vec(n, UInt(d.W)))
  private var extraNestedStreamsPruned = false

  if (r) {
    do_flip()
  }

  /** Private do_flip function that executes the flip, depth-first, but does not influence [[r]] */
  private def do_flip(): Unit = {
    val subStreams: Seq[PhysicalStreamDetailed[_, _]] = getStreamElements
    for (subStream <- subStreams) {
      subStream.flip
    }
  }

  /**
   * In the Tydi standard, only one nested stream is exposed, even if the parent has multiple lanes.
   * In Tydi-lib, each lane has its own nested stream copy. Therefore, this method discards each "extra"
   * nested stream by connecting it to `DontCare`.
   */
  private def dumpExtraNestedStreams(): Unit = {
    if (extraNestedStreamsPruned) return
    extraNestedStreamsPruned = true
    val extraStreams = data.tail.flatMap(_.getStreamElements)
    for (stream <- extraStreams) {
      stream := DontCare
    }
  }

  override def getDataType: Tel = e
  override def getUserType: Tus = u

  override def getDataConcat: UInt = data.map(_.getDataConcat).reduce((a, b) => Cat(b, a))

  def getUserConcat: UInt = user.asUInt

  override def getDataElementsRec: Seq[Data] = data.flatMap(_.getDataElementsRec)

  override def getStreamElements: Seq[PhysicalStreamDetailed[_, _]] = data.flatMap(_.getStreamElements)

  def getUserElements: Seq[Data] = user match {
    case x: Bundle => x.getElements
    case x: Data   => x :: Nil
  }

  def el: Tel = data(0)

  /** This public method flips a stream's and sub-streams' directions. */
  def flip: PhysicalStreamDetailed[Tel, Tus] = {
    r = !r
    do_flip()
    this
  }

  def toPhysical: PhysicalStream = {
    dumpExtraNestedStreams()
    val flip   = r
    val stream = new PhysicalStream(e, n, d, c, u)
    val io     = IO(if (flip) Flipped(stream) else stream)
    if (flip) {
      this := io
    } else {
      io := this
    }
    io
  }

  // Require the element and user signal types to be the same as this stream in the function signature.
  /**
   * Stream mounting function.
   * @param bundle Source stream to drive this stream with.
   */
  private[tydi_chisel] def connectDetailed[TBel <: TydiEl, TBus <: Data](
    bundle: PhysicalStreamDetailed[TBel, TBus],
    typeCheck: CompatCheck.Value = CompatCheck.Strict,
    errorReporting: CompatCheckResult.Value
  ): Unit = {
    if (!bundle.r || this.r) {
      throw new Exception("Attempting to connect an input to an output. Reverse the connection.")
    }
    // The following would work if we would know with certainty that the signals are oriented the right way,
    // but we do not -.-
    // (this.data: Data) :<>= (bundle.data: Data)

    // Using the recursive function leads to duplicate connections when connecting sub-streams, but the non-recursive
    // version cannot be used, since non-stream elements could still contain stream items.
    for ((thisData, bundleData) <- this.getDataElementsRec.zip(bundle.getDataElementsRec)) {
      thisData :<>= bundleData
    }
    for (
      (thisStream: PhysicalStreamDetailed[_, _], bundleStream: PhysicalStreamDetailed[_, _]) <- this.getStreamElements
        .zip(bundle.getStreamElements)
    ) {
      thisStream := bundleStream
    }
    (this.user: Data) :<>= (bundle.user: Data)
  }

  /**
   * Stream mounting function.
   * @param bundle Source stream to drive this stream with.
   */
  private[tydi_chisel] def connectSimple(
    bundle: PhysicalStream,
    typeCheck: CompatCheck.Value = CompatCheck.Strict,
    errorReporting: CompatCheckResult.Value
  ): Unit = {
    // Connect data bitvector back to bundle
    for ((dataLane, i) <- this.data.zipWithIndex) {
      val dataWidth = bundle.elWidth
      dataLane.getDataElementsRec.reverse.foldLeft(i * dataWidth)((j, dataField) => {
        val width = dataField.getWidth
        // .asTypeOf cast is necessary to prevent incompatible type errors
        dataField := bundle.data(j + width - 1, j).asTypeOf(dataField)
        j + width
      })
    }
    // Connect user bitvector back to bundle
    // Todo: Investigate if this is really necessary or if connecting as Data Bundle/Vector directly is fine,
    //  since user signals are unspecified by the standard.
    this.getUserElements.foldLeft(0)((i, userField) => {
      val width = userField.getWidth
      userField := bundle.user(i + width - 1, i)
      i + width
    })
  }
}

object PhysicalStreamDetailed {
  def apply[Tel <: TydiEl, Tus <: Data](
    e: Tel,
    n: Int = 1,
    d: Int = 1,
    c: Int,
    r: Boolean = false,
    u: Tus = Null()
  ): PhysicalStreamDetailed[Tel, Tus] = Wire(new PhysicalStreamDetailed(e, n, d, c, r, u))
}

trait TydiModuleMixin extends BaseModule with TranspileExtend {
  implicit var parentModule: TydiModuleMixin = this

  private val moduleList = ListBuffer[TydiModuleMixin]()

  def mount[Tel <: TydiEl, Tus <: Data](bundle: PhysicalStreamDetailed[Tel, Data], io: PhysicalStream): Unit = {
    io := bundle
  }

  def Module[T <: BaseModule](bc: => T): T = {
    val v = chisel3.Module.apply(bc)
    // If we are actually dealing with a TydiModule, add it to our module list.
    v match {
      case v: TydiModuleMixin => moduleList += v
    }
    v
  }

  override def getModulePorts: Seq[Data] = super.getModulePorts

  /**
   * Tydi-lang cannot handle "in" or "out" as signal name, therefore these are prefixed with "std_".
   * This function adds that prefix.
   *
   * @param name Unprocessed name
   * @return Name with "std_" prefix added if name is "in" or "out"
   */
  private def filterPortName(name: String): String = {
    if (name == "in" || name == "out") "std_" + name else name
  }

  override def transpile(_map: mutable.LinkedHashMap[String, String]): mutable.LinkedHashMap[String, String] = {
    var map = _map

    for (module <- moduleList) {
      map = module.transpile(map)
    }

    val ports: Seq[PhysicalStream] = getModulePorts
      .filter {
        case _: PhysicalStream => true
        case _                 => false
      }
      .map(_.asInstanceOf[PhysicalStream])

    for (elem <- ports) {
      map = elem.transpile(map)
    }
    val moduleName    = this.name
    val streamletName = s"${moduleName}_interface"

    var str = s"streamlet $streamletName {\n"
    for (elem <- ports) {
      val instanceName = filterPortName(elem.instanceName)
      val containsOut  = instanceName.toLowerCase.contains("out")
      val containsIn   = instanceName.toLowerCase.contains("in")
      val dirWord      = if (containsOut) "out" else if (containsIn) "in" else "unknown"
      str += s"    $instanceName : ${elem.fingerprint} $dirWord;\n"
    }
    str += "}"
    map += (streamletName -> str)

    str = s"impl $moduleName of $streamletName {\n"
    for (port <- ports) {
      str += s"    self.${filterPortName(port.instanceName)} => ...;\n"
    }
    for (module <- moduleList) {
      val instanceName = module.instanceName
      str += s"    instance $instanceName(${module.name});\n"
      val modulePorts: Seq[PhysicalStream] = module.getModulePorts
        .filter {
          case _: PhysicalStream => true
          case _                 => false
        }
        .map(_.asInstanceOf[PhysicalStream])
      for (port <- modulePorts) {
        str += s"    $instanceName.${filterPortName(port.instanceName)} => ...;\n"
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

abstract class TydiModule    extends Module with TydiModuleMixin
abstract class TydiExtModule extends ExtModule with TydiModuleMixin
