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
    val stringBuilder = new StringBuilder

    stringBuilder.append(s"State of \"${x.instanceName}\" @ step ${getSinkClock.getStepCount}:\n")
    stringBuilder.append(s"valid: ${x.valid.peek().litToBoolean}\n")
    stringBuilder.append(s"ready: ${x.ready.peek().litToBoolean}\n")
    stringBuilder.append(s"last: ${x.last.peek()}\n")
    stringBuilder.append(s"stai: ${x.stai.peek().litValue}\n")
    stringBuilder.append(s"endi: ${x.endi.peek().litValue}\n")
    stringBuilder.append(s"strb: ${x.strb.peek()}\n")

    x.data.zipWithIndex.foreach { case (lane, index) =>
      stringBuilder.append(s"Lane $index: ${lane.peek()}\n")
    }

    stringBuilder.toString
  }
}

object TydiStreamDriver {
  protected val decoupledSourceKey = new Object()
  protected val decoupledSinkKey = new Object()
}
