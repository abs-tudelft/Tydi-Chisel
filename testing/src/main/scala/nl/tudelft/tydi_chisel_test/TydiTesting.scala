package nl.tudelft.tydi_chisel_test

import scala.language.implicitConversions

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.{AddObjectLiteralConstructor, AddVecLiteralConstructor}
import chisel3.util._
import chiseltest._
import nl.tudelft.tydi_chisel.{PhysicalStreamDetailed, TydiEl}

object Conversions {
  implicit def tydiStreamToDriver[Tel <: TydiEl, Tus <: Data](
    x: PhysicalStreamDetailed[Tel, Tus]
  ): TydiStreamDriver[Tel, Tus] = new TydiStreamDriver(x)
}

// implicit class, cannot maintain state
class TydiStreamDriver[Tel <: TydiEl, Tus <: Data](x: PhysicalStreamDetailed[Tel, Tus]) {
  // Source (enqueue) functions
  //
  def initSource(): this.type = {
    x.valid.poke(false)
    x.stai.poke(0.U)
    x.endi.poke((x.n - 1).U)
    x.strb.poke(((1 << x.n) - 1).U(x.n.W)) // Set strobe to all 1's
    if (x.d > 0) {
      val lasts: Seq[UInt] = Seq.fill(x.n)(0.U(x.d.W))
      x.last.poke(Vec.Lit(lasts: _*))
    }
    this
  }

  def elLit(elems: (Tel => (Data, Data))*): Tel = {
    // Must use datatype instead of just .data or .el because Lit does not accept hardware types.
    // Use splat operator to propagate repeated parameters
    x.getDataType.Lit(elems: _*)
  }

  def dataLit(elems: (Int, Tel)*): Vec[Tel] = {
    Vec(x.n, x.getDataType).Lit(elems: _*)
  }

  def lastLit(elems: (Int, UInt)*): Vec[UInt] = {
    Vec(x.n, UInt(x.d.W)).Lit(elems: _*)
  }

  private def _enqueueNow(
    data: Option[Vec[Tel]],
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {},
    reset: Boolean = false
  ): Unit = {
    if (data.isDefined) {
      x.data.pokePartial(data.get)
    }
    if (last.isDefined) {
      x.last.pokePartial(last.get)
    }
    if (strb.isDefined) {
      x.strb.poke(strb.get)
    }
    if (stai.isDefined) {
      x.stai.poke(stai.get)
    }
    if (endi.isDefined) {
      x.endi.poke(endi.get)
    }
    x.valid.poke(true)
    run
    fork
      .withRegion(Monitor) {
        x.ready.expect(true)
      }
      .joinAndStep()
    x.valid.poke(false)
    if (reset) { initSource() }
  }

  def enqueueElNow(
    data: Tel,
    last: Option[UInt] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {},
    reset: Boolean = false
  ): Unit = {
    val lastLit = if (last.isDefined) {
      Option(Vec(x.n, UInt(x.d.W)).Lit(0 -> last.get))
    } else {
      None
    }
    _enqueueNow(Option(dataLit(0 -> data)), lastLit, strb, stai, endi, run, reset)
  }

  def enqueueNow(
    data: Vec[Tel],
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {},
    reset: Boolean = false
  ): Unit = {
    _enqueueNow(Option(data), last, strb, stai, endi, run, reset)
  }

  /** Send an empty transfer (no valid data lanes). Unless overridden, a strobe of 0's is sent. */
  def enqueueEmptyNow(
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {},
    reset: Boolean = false
  ): Unit = {
    val _strb = if (strb.isDefined) {
      strb
    } else {
      Option(0.U)
    }
    _enqueueNow(None, last, _strb, stai, endi, run, reset)
  }

  def enqueueElNow(elems: (Tel => (Data, Data))*): Unit = {
    val litValue = elLit(elems: _*) // Use splat operator to propagate repeated parameters
    enqueueElNow(litValue)
  }

  def enqueue(data: Tel): Unit = {
    x.el.poke(data)
    x.valid.poke(true)
    fork
      .withRegion(Monitor) {
        while (!x.ready.peekBoolean()) {
          step(1)
        }
      }
      .joinAndStep()
    x.valid.poke(true)
  }

  def enqueue(elems: (Tel => (Data, Data))*): Unit = {
    val litValue = elLit(elems: _*) // Use splat operator to propagate repeated parameters
    enqueue(litValue)
  }

  def enqueueSeq(data: Seq[Tel]): Unit = {
    for (elt <- data) {
      enqueue(elt)
    }
  }

  // Sink (dequeue) functions
  //
  def initSink(): this.type = {
    x.ready.poke(false)
    this
  }

  @deprecated("You no longer need to set the clock explicitly.", since = "6.0.x")
  protected val decoupledSourceKey            = new Object()
  def setSourceClock(clock: Clock): this.type = this
  protected val decoupledSinkKey              = new Object()
  @deprecated("You no longer need to set the clock explicitly.", since = "6.0.x")
  def setSinkClock(clock: Clock): this.type = this

  // NOTE: this doesn't happen in the Monitor phase, unlike public functions
  def waitForValid(): Unit = {
    while (!x.valid.peek().litToBoolean) {
      step(1)
    }
  }

  def expectDequeue(data: Tel): Unit = {
    x.ready.poke(true)
    fork
      .withRegion(Monitor) {
        waitForValid()
        x.valid.expect(true)
        x.el.expect(data)
      }
      .joinAndStep()
    x.ready.poke(false)
  }

  def expectDequeue(elems: (Tel => (Data, Data))*): Unit = {
    val litValue = elLit(elems: _*) // Use splat operator to propagate repeated parameters
    expectDequeue(litValue)
  }

  private def _expectDequeueNow(
    data: Option[Tel],
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {}
  ): Unit = {
    x.ready.poke(true)
    fork
      .withRegion(Monitor) {
        x.valid.expect(true)
        run
        if (data.isDefined) {
          x.el.expect(data.get)
        }
        if (last.isDefined) {
          x.last.expect(last.get)
        }
        if (stai.isDefined) {
          x.stai.expect(stai.get)
        }
        if (endi.isDefined) {
          x.endi.expect(endi.get)
        }
        if (strb.isDefined) {
          x.strb.expect(strb.get)
        }
      }
      .joinAndStep()
    x.ready.poke(false)
  }

  def expectDequeueNow(
    data: Tel,
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {}
  ): Unit = {
    _expectDequeueNow(Option(data), last, strb, stai, endi, run)
  }

  /** Expect an empty transfer (no valid data lanes). Unless overridden, a strobe of 0's is expected. */
  def expectDequeueEmptyNow(
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {}
  ): Unit = {
    val _strb = if (strb.isDefined) {
      strb
    } else {
      Option(0.U)
    }
    _expectDequeueNow(None, last, _strb, stai, endi, run)
  }

  def expectDequeueNow(elems: (Tel => (Data, Data))*): Unit = {
    val litValue = elLit(elems: _*) // Use splat operator to propagate repeated parameters
    expectDequeueNow(litValue)
  }

  def expectDequeueSeq(data: Seq[Tel]): Unit = {
    for (elt <- data) {
      expectDequeue(elt)
    }
  }

  def expectPeek(data: Tel): Unit = {
    fork.withRegion(Monitor) {
      x.valid.expect(true)
      x.el.expect(data)
    }
  }

  def expectInvalid(): Unit = {
    fork.withRegion(Monitor) {
      x.valid.expect(false)
    }
  }

  def printState(renderer: Tel => String = _.toString()): String = {
    import printUtils._
    val stringBuilder = new StringBuilder

    val out                                = '↑'
    val in                                 = '↓'
    def logicSymbol(cond: Boolean): String = if (cond) "✔" else "✖"
    val streamDir                          = if (x.r) in else out
    val streamAntiDir                      = if (x.r) out else in

    try
      stringBuilder.append(
        s"State of \"${x.instanceName}\" $streamDir @ clk-step ${x.getSourceClock().getStepCount}:\n"
      )
    catch {
      case e: ClockResolutionException =>
        stringBuilder.append(s"State of \"${x.instanceName}\" $streamDir (unable to get clock):\n")
    }
    // Valid and ready signals
    stringBuilder.append(s"valid $streamDir: ${logicSymbol(x.valid.peekBoolean())}\t\t\t")
    stringBuilder.append(s"ready $streamAntiDir: ${logicSymbol(x.ready.peekBoolean())}\n")
    // Stai and endi signals
    stringBuilder.append(s"stai ≥: ${x.stai.peek().litValue}\t\t\t")
    stringBuilder.append(s"endi ≤: ${x.endi.peek().litValue}\n")

    // Strobe signal
    if (x.c < 8) {
      // For C<8 all `strb` bits should be the same
      stringBuilder.append(s"strb: ${x.strb.peek()(0).litToBoolean} (${binaryFromUint(x.strb.peek())})\n")
    } else {
      stringBuilder.append(s"strb: ${binaryFromUint(x.strb.peek())}\n")
    }
    // Last signal
    if (x.c < 8) {
      stringBuilder.append(s"last: ${binaryFromUint(x.last.last.peek(), empty = "-")}\n")
    } else {
      stringBuilder.append(s"last: ${x.last.peek().map(binaryFromUint(_, empty = "-")).mkString("|")}\n")
    }

    // Lane-specific info
    stringBuilder.append("Lanes:\n")
    x.data.zipWithIndex.foreach { case (lane, index) =>
      // Print data
      val dataString = renderer(lane.peek())
      stringBuilder.append(s"$index\tdata: $dataString\n")

      // Last signal for this lane
      if (x.c >= 8) {
        stringBuilder.append(s"\tlast: ${binaryFromUint(x.last(index).peek(), empty = "-")}\n")
      }

      // See if a lane is active or not and why
      val active_strobe = x.strb.peek()(index).litToBoolean
      val active_stai   = index >= x.stai.peek().litValue
      val active_endi   = index <= x.endi.peek().litValue
      val active        = active_strobe && active_stai && active_endi
      stringBuilder.append(
        s"\tactive: ${logicSymbol(active)} \t\t(strb=${logicSymbol(active_strobe)}; stai=${logicSymbol(active_stai)}; endi=${logicSymbol(active_endi)};)\n"
      )
    }

    stringBuilder.toString
  }
}

object printUtils {
  def printVecBinary[T <: Data](vec: Vec[T]): String = {
    vec.map(v => binaryFromData(v)).mkString(", ")
  }

  def printVec[T <: Data](vec: Vec[T]): String = {
    vec.map(v => v.litValue).mkString(", ")
  }

  def binaryFromData[T <: Data](num: T, width: Option[Int] = None, empty: String = ""): String =
    binaryFromUint(num.asUInt, width, empty)

  def binaryFromUint(num: UInt, width: Option[Int] = None, empty: String = ""): String =
    binaryFromInt(num.litValue.toInt, width.getOrElse(num.getWidth), empty)

  def binaryFromBigInt(num: BigInt, width: Int, empty: String = ""): String = binaryFromInt(num.toInt, width, empty)

  /**
   * Get binary notation of a number of specified width.
   * @param num Number to transform
   * @param width Requested width of the binary string
   * @return String with binary notation
   */
  def binaryFromInt(num: Int, width: Int, empty: String = ""): String = {
    if (width == 0) return empty
    String.format("%" + width + "s", num.toBinaryString).replace(' ', '0')
  }
}
