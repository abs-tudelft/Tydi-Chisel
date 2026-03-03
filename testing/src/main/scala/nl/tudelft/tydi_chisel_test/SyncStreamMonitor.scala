package nl.tudelft.tydi_chisel_test

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.{AddObjectLiteralConstructor, AddVecLiteralConstructor}
import chisel3.simulator.PeekPokeAPI._
import nl.tudelft.tydi_chisel.{PhysicalStreamDetailed, TydiEl}

class SyncStreamMonitor[Tel <: TydiEl, Tus <: Data](source: PhysicalStreamDetailed[Tel, Tus], clockSig: Option[Clock])
      extends StreamMetaUtil[Tel, Tus] {

  private val n = source.n
  /**
   * Initialises/resets the signals of the source in the following way:
   *
   * <ul>
   * <li>Ready: low</li>
   * </ul>
   * @return This driver
   */
  private def initWithSource(): this.type = {
    source.ready.poke(false)
    this
  }

  initWithSource()

  /**
   * Resets the signals of the source in the following way:
   *
   * <ul>
   * <li>Ready: low</li>
   * </ul>
   * @return This driver
   */
  def reset(): SyncStreamMonitor.this.type = initWithSource()

  def elLit(elems: (Tel => (Data, Data))*): Tel = {
    // Must use datatype instead of just .data or .el because Lit does not accept hardware types.
    // Use splat operator to propagate repeated parameters
    source.getDataType.Lit(elems: _*)
  }

  def dataLit(elems: (Int, Tel)*): Vec[Tel] = {
    Vec(elems.length, source.getDataType).Lit(elems: _*)
  }

  def lastLit(elems: (Int, UInt)*): Vec[UInt] = {
    Vec(elems.length, UInt(source.d.W)).Lit(elems: _*)
  }

  /**
   * Checks specified values on the source stream's signals with `expect` calls.
   *
   * @param data Vector of `n` stream element literals, one for each lane
   * @param last Vector of `n` last `d`-bits values, one for each lane
   * @param strb Strobe value of `n` bits
   * @param stai Start index of lane validity, inclusive
   * @param endi End index of lane validity, inclusive
   * @param run Optional function to run after the `expect` checking
   * @param step Whether to step the clock after expecting and executing `run`
   * @param reset Whether to reset the sink signals at the end, check the `reset` method. This usually only makes sense when stepping the clock.
   */
  private def _expect(
    data: Option[Tel],
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {},
    step: Boolean = false,
    reset: Boolean = false
  ): Unit = {
    source.ready.poke(true)
    source.valid.expect(true.B)
    run
    if (data.isDefined) {
      source.el.expect(data.get)
    }
    if (last.isDefined) {
      // Todo, should there be a warning when the lengths are not the same?
      0 until n foreach { i =>
        val lastLane = if (i < last.get.length) Some(last.get(i)) else None
        if (lastLane.isDefined) {
          source.last(i).expect(lastLane.get)
        }
      }
    }
    if (stai.isDefined) {
      source.stai.expect(stai.get)
    }
    if (endi.isDefined) {
      source.endi.expect(endi.get)
    }
    if (strb.isDefined) {
      source.strb.expect(strb.get)
    }
    if (step) {
      if (clockSig.isDefined) {
        clockSig.get.step(1)
      } else {
        throw new RuntimeException("Clock signal not set")
      }
    }
    if (reset) { this.reset() }
  }

  // Fixme, this is inconsistent with the driver
  /**
   * Checks specified values on the source stream's signals with `expect` calls, specifically for the first lane.
   *
   * @param data Single data element literal
   * @param last Vector of `n` last `d`-bits values, one for each lane
   * @param strb Strobe value of `n` bits
   * @param stai Start index of lane validity, inclusive
   * @param endi End index of lane validity, inclusive
   * @param run Optional function to run after the `expect` checking
   * @param step Whether to step the clock after expecting and executing `run`
   * @param reset Whether to reset the sink signals at the end, check the `reset` method. This usually only makes sense when stepping the clock.
   */
  def expect(
    data: Tel,
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {},
    step: Boolean = false,
    reset: Boolean = false
  ): Unit = {
    _expect(Option(data), last, strb, stai, endi, run, step, reset)
  }

  /**
   * Expect an empty transfer (no valid data lanes). Unless overridden, a strobe of 0's is expected.
   * @param last Vector of `n` last `d`-bits values, one for each lane
   * @param strb Strobe value of `n` bits
   * @param stai Start index of lane validity, inclusive
   * @param endi End index of lane validity, inclusive
   * @param run Optional function to run after the `expect` checking
   * @param step Whether to step the clock after expecting and executing `run`
   * @param reset Whether to reset the sink signals at the end, check the `reset` method. This usually only makes sense when stepping the clock.
   */
  def expectEmpty(
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {},
    step: Boolean = false,
    reset: Boolean = false
  ): Unit = {
    val _strb = if (strb.isDefined) {
      strb
    } else {
      Option(0.U)
    }
    _expect(None, last, _strb, stai, endi, run, step, reset)
  }

  /**
   * A shorthand function to check if the source can ''immediately'' dequeue a literal in the first lane with the values
   * specified as the function arguments.<br>
   * Note that this function will '''always''' step the `clock` and reset the source, meaning `valid` is turned off.
   * This is because the splatting disallows other keywords afterwards and before is inconvenient.
   * @param elems Tuples specifying bundle field values
   */
  def expect(elems: (Tel => (Data, Data))*): Unit = {
    val litValue = elLit(elems: _*) // Use splat operator to propagate repeated parameters
    expect(litValue, step = true, reset = true)
  }

  /**
   * Steps the clock until the source's `valid` signal is asserted
   */
  def waitForValid(): Unit = {
    if (clockSig.isEmpty) {
      throw new RuntimeException("Clock signal not set")
    }
    while (!source.valid.peek().litToBoolean) {
      clockSig.get.step(1)
    }
  }

  /*def expectPeek(data: Tel): Unit = {
    source.valid.expect(true.B)
    source.el.expect(data)
  }*/

  /**
   * Do an `expect` check on the source's `valid` signal, expecting it to be low.
   */
  def expectInvalid(): Unit = {
    source.valid.expect(false.B)
  }

  var renderer: Tel => String = _.toString()

  def printState: String = _printState(source, renderer)

}

object SyncStreamMonitor {
  def apply[Tel <: TydiEl, Tus <: Data](source: PhysicalStreamDetailed[Tel, Tus], clockSig: Option[Clock] = None) = new SyncStreamMonitor(source, clockSig)
}
