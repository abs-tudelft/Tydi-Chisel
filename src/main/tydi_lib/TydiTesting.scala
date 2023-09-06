package tydi_lib.testing

import chiseltest._
import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.AddVecLiteralConstructor
import tydi_lib.{PhysicalStreamDetailed, TydiEl}
import scala.language.implicitConversions

object Conversions {
  implicit def tydiStreamToDriver[Tel <: TydiEl, Tus <: Data](x: PhysicalStreamDetailed[Tel, Tus]): TydiStreamDriver[Tel, Tus] = new TydiStreamDriver(x)
}

// implicit class, cannot maintain state
class TydiStreamDriver[Tel <: TydiEl, Tus <: Data](x: PhysicalStreamDetailed[Tel, Tus]) {
  // Source (enqueue) functions
  //
  def initSource(): this.type = {
    x.valid.poke(false.B)
    x.stai.poke(0.U)
    x.endi.poke(0.U)
    x.strb.poke(((1 << x.n)-1).U(x.n.W)) // Set strobe to all 1's
//    val lasts = (0 until x.n).map(index => (index, 0.U))
//    x.last.poke(x.last.Lit(lasts: _*))
    this
  }

  def setSourceClock(clock: Clock): this.type = {
    ClockResolutionUtils.setClock(TydiStreamDriver.decoupledSourceKey, x, clock)
    this
  }

  protected def getSourceClock: Clock = {
    ClockResolutionUtils.getClock(
      TydiStreamDriver.decoupledSourceKey,
      x,
      x.ready.getSourceClock()
    ) // TODO: validate against bits/valid sink clocks
  }

  def dataLit(elems: (Tel => (Data, Data))*): Tel = {
    // Must use datatype instead of just .data or .el because Lit does not accept hardware types.
    // Use splat operator to propagate repeated parameters
    x.getDataType.Lit(elems: _*)
  }

  def enqueueNow(data: Tel): Unit = timescope {
    // TODO: check for init
    x.el.poke(data)
    x.valid.poke(true.B)
    fork
      .withRegion(Monitor) {
        x.ready.expect(true.B)
      }
      .joinAndStep(getSourceClock)
  }

  def enqueueNow(elems: (Tel => (Data, Data))*): Unit = timescope {
    val litValue = dataLit(elems: _*) // Use splat operator to propagate repeated parameters
    enqueueNow(litValue)
  }

  def enqueue(data: Tel): Unit = timescope {
    // TODO: check for init
    x.el.poke(data)
    x.valid.poke(true.B)
    fork
      .withRegion(Monitor) {
        while (x.ready.peek().litToBoolean == false) {
          getSourceClock.step(1)
        }
      }
      .joinAndStep(getSourceClock)
  }

  def enqueue(elems: (Tel => (Data, Data))*): Unit = timescope {
    val litValue = dataLit(elems:_*) // Use splat operator to propagate repeated parameters
    enqueue(litValue)
  }

  def enqueueSeq(data: Seq[Tel]): Unit = timescope {
    for (elt <- data) {
      enqueue(elt)
    }
  }

  // Sink (dequeue) functions
  //
  def initSink(): this.type = {
    x.ready.poke(false.B)
    this
  }

  def setSinkClock(clock: Clock): this.type = {
    ClockResolutionUtils.setClock(TydiStreamDriver.decoupledSinkKey, x, clock)
    this
  }

  protected def getSinkClock: Clock = {
    ClockResolutionUtils.getClock(
      TydiStreamDriver.decoupledSinkKey,
      x,
      x.valid.getSourceClock()
    ) // TODO: validate against bits/valid sink clocks
  }

  // NOTE: this doesn't happen in the Monitor phase, unlike public functions
  def waitForValid(): Unit = {
    while (!x.valid.peek().litToBoolean) {
      getSinkClock.step(1)
    }
  }

  def expectDequeue(data: Tel): Unit = timescope {
    // TODO: check for init
    x.ready.poke(true.B)
    fork
      .withRegion(Monitor) {
        waitForValid()
        x.valid.expect(true.B)
        x.el.expect(data)
      }
      .joinAndStep(getSinkClock)
  }

  def expectDequeue(elems: (Tel => (Data, Data))*): Unit = timescope {
    val litValue = dataLit(elems: _*) // Use splat operator to propagate repeated parameters
    expectDequeue(litValue)
  }

  def expectDequeueNow(data: Tel): Unit = timescope {
    // TODO: check for init
    x.ready.poke(true.B)
    fork
      .withRegion(Monitor) {
        x.valid.expect(true.B)
        x.el.expect(data)
      }
      .joinAndStep(getSinkClock)
  }

  def expectDequeueNow(elems: (Tel => (Data, Data))*): Unit = timescope {
    val litValue = dataLit(elems: _*) // Use splat operator to propagate repeated parameters
    expectDequeueNow(litValue)
  }

  def expectDequeueSeq(data: Seq[Tel]): Unit = timescope {
    for (elt <- data) {
      expectDequeue(elt)
    }
  }

  def expectPeek(data: Tel): Unit = {
    fork.withRegion(Monitor) {
      x.valid.expect(true.B)
      x.el.expect(data)
    }
  }

  def expectInvalid(): Unit = {
    fork.withRegion(Monitor) {
      x.valid.expect(false.B)
    }
  }

  def printState(): String = {
    import printUtils._
    val stringBuilder = new StringBuilder

    val out = '↑'
    val in = '↓'
    val streamDir = if (x.r) in else out
    val streamAntiDir = if (x.r) out else in

    try
      stringBuilder.append(s"State of \"${x.instanceName}\" $streamDir @ clk-step ${getSinkClock.getStepCount}:\n")
    catch {
      case e: ClockResolutionException => stringBuilder.append(s"State of \"${x.instanceName}\" $streamDir (unable to get clock):\n")
    }
    // Valid and ready signals
    stringBuilder.append(s"valid $streamDir: ${x.valid.peek().litToBoolean}\t\t")
    stringBuilder.append(s"ready $streamAntiDir: ${x.ready.peek().litToBoolean}\n")
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
      stringBuilder.append(s"last: ${binaryFromUint(x.last.last.peek(), empty="-")}\n")
    } else {
      stringBuilder.append(s"last: ${x.last.peek().map(binaryFromUint(_, empty="-")).mkString("|")}\n")
    }

    // Lane-specific info
    stringBuilder.append("Lanes:\n")
    x.data.zipWithIndex.foreach { case (lane, index) =>
      // Print data
      stringBuilder.append(s"$index\tdata: ${lane.peek()}\n")

      // Last signal for this lane
      if (x.c >= 8) {
        stringBuilder.append(s"\tlast: ${binaryFromUint(x.last(index).peek(), empty="-")}\n")
      }

      // See if a lane is active or not and why
      val active_strobe = x.strb.peek()(index).litToBoolean
      val active_stai = x.stai.peek().litValue >= index
      val active_endi = x.endi.peek().litValue <= index
      val active = active_strobe && active_stai && active_endi
      stringBuilder.append(s"\tactive: $active \t(strb=$active_strobe; stai=$active_stai; endi=$active_endi;)\n")
    }

    stringBuilder.toString
  }
}

object TydiStreamDriver {
  protected val decoupledSourceKey = new Object()
  protected val decoupledSinkKey = new Object()
}


object printUtils {
  def printVecBinary[T <: Data](vec: Vec[T]): String = {
    vec.map(v => binaryFromData(v)).mkString(", ")
  }

  def printVec[T <: Data](vec: Vec[T]): String = {
    vec.map(v => v.litValue).mkString(", ")
  }

  def binaryFromData[T <: Data](num: T, width: Option[Int] = None, empty: String = ""): String = binaryFromUint(num.asUInt, width, empty)

  def binaryFromUint(num: UInt, width: Option[Int] = None, empty: String = ""): String = binaryFromInt(num.litValue.toInt, width.getOrElse(num.getWidth), empty)

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
