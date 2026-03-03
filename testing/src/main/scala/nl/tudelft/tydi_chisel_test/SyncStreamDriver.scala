package nl.tudelft.tydi_chisel_test

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.{AddObjectLiteralConstructor, AddVecLiteralConstructor}
import chisel3.simulator.PeekPokeAPI._
import nl.tudelft.tydi_chisel.{PhysicalStreamDetailed, TydiEl}
import org.scalatest.run

class SyncStreamDriver[Tel <: TydiEl, Tus <: Data](sink: PhysicalStreamDetailed[Tel, Tus], clockSig: Option[Clock])
      extends StreamMetaUtil[Tel, Tus] {

  private val n = sink.n

  /**
   * Initialises/resets the signals of the sink in the following way:
   *
   * <ul>
   * <li>Valid: low</li>
   * <li>Lane validity: full (strobe bits and start/end indexes)</li>
   * <li>Last values: empty (all `0`)</li>
   * </ul>
   * @return This driver
   */
  private def initWithSink(): this.type = {
    sink.valid.poke(false)
    if (n > 1) {
      sink.stai.poke(0.U)
      sink.endi.poke((sink.n - 1).U)
    }
    sink.strb.poke(((1 << sink.n) - 1).U(sink.n.W)) // Set strobe to all 1's
    if (sink.d > 0) {
      val lasts: Seq[UInt] = Seq.fill(sink.n)(0.U(sink.d.W))
      sink.last.poke(Vec.Lit(lasts: _*))
    }
    this
  }

  initWithSink()

  /**
   * Resets the signals of the sink in the following way:
   *
   * <ul>
   * <li>Valid: low</li>
   * <li>Lane validity: full (strobe bits and start/end indexes)</li>
   * <li>Last values: empty (all `0`)</li>
   * </ul>
   * @return This driver
   */
  def reset(): SyncStreamDriver.this.type = initWithSink()

  def elLit(elems: (Tel => (Data, Data))*): Tel = {
    // Must use datatype instead of just .data or .el because Lit does not accept hardware types.
    // Use splat operator to propagate repeated parameters
    sink.getDataType.Lit(elems: _*)
  }

  def dataLit(elems: (Int, Tel)*): Vec[Tel] = {
    Vec(elems.length, sink.getDataType).Lit(elems: _*)
  }

  def lastLit(elems: (Int, UInt)*): Vec[UInt] = {
    Vec(elems.length, UInt(sink.d.W)).Lit(elems: _*)
  }

  /**
   * Loads specified values on the sink stream's signals. Any optional element that is not specified does not get
   * modified from the current state.
   *
   * @param data Vector of `n` stream element literals, one for each lane
   * @param last Vector of `n` last `d`-bits values, one for each lane
   * @param strb Strobe value of `n` bits
   * @param stai Start index of lane validity, inclusive
   * @param endi End index of lane validity, inclusive
   * @param run Optional function to run after the poking
   * @param step Whether to step the clock after poking and executing `run`
   * @param reset Whether to reset the sink signals at the end, check the `reset` method. This usually only makes sense when stepping the clock.
   */
  private def _poke(
    data: Option[Vec[Tel]],
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {},
    step: Boolean = false,
    reset: Boolean = false
  ): Unit = {
    if (data.isDefined) {
      var strbData = 0
      0 until n foreach { i =>
        val lanePacket = if (i < data.get.length) Some(data.get(i)) else None
        if (lanePacket.isDefined) {
          sink.data(i).poke(lanePacket.get)
          strbData = (strbData << 1) + 1
        } else {
//          sink.data(i).poke(0.U)
          strbData = strbData << 1
        }
      }
      sink.strb.poke(strbData)
    }
    if (last.isDefined) {
      0 until n foreach { i =>
        val lastLane = if (i < last.get.length) Some(last.get(i)) else None
        if (lastLane.isDefined) {
          sink.last(i).poke(lastLane.get)
        } else {
          sink.last(i).poke(0)
        }
      }
    }
    if (strb.isDefined) {
      sink.strb.poke(strb.get)
    }
    if (stai.isDefined && n > 1) {
      sink.stai.poke(stai.get)
    }
    if (endi.isDefined && n > 1) {
      sink.endi.poke(endi.get)
    }
    sink.ready.expect(true.B)
    sink.valid.poke(true)
    run
    if (step) {
      if (clockSig.isDefined) {
        clockSig.get.step(1)
      } else {
        throw new RuntimeException("Clock signal not set")
      }
    }
    if (reset) { this.reset() }
  }

  // Fixme: does it make sense that I allow specifying the whole strobe here?
  /**
   * Loads the data signals on the sink, specifically for the first lane. Any optional element that is not specified
   * does not get modified from the current state.
   * @param data Single data element literal
   * @param last `d`-bits `last` value
   * @param strb `n`-bits strobe value
   * @param stai Optional start index of lane validity, inclusive
   * @param endi Optional end index of lane validity, inclusive
   * @param run Optional function to run after the poking
   * @param step Whether to step the clock after poking and executing `run`
   * @param reset Whether to reset the sink signals at the end, check the `reset` method. This usually only makes sense when stepping the clock.
   */
  def pokeEl(
    data: Tel,
    last: Option[UInt] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {},
    step: Boolean = false,
    reset: Boolean = false
  ): Unit = {
    val lastLit = if (last.isDefined) {
      // Create list where every element is 0.U, except the last one, which is equal to the content of the `last` arg
      val lastValues = List.fill(sink.n-1)(0 -> 0.U) ::: List((n-1) -> last.get)
      Option(Vec(sink.n, UInt(sink.d.W)).Lit(lastValues: _*))
    } else {
      None
    }
    _poke(Option(dataLit(0 -> data)), lastLit, strb, stai, endi, run, step, reset)
  }

  /**
   * Loads specified values on the sink stream's signals. Any optional element that is not specified does not get
   * modified from the current state.
   *
   * @param data Vector of `n` stream element literals, one for each lane
   * @param last Vector of `n` last `d`-bits values, one for each lane
   * @param strb Strobe value of `n` bits
   * @param stai Start index of lane validity, inclusive
   * @param endi End index of lane validity, inclusive
   * @param run Optional function to run after the poking
   * @param step Whether to step the clock after poking and executing `run`
   * @param reset Whether to reset the sink signals at the end, check the `reset` method. This usually only makes sense when stepping the clock.
   */
  def poke(
    data: Vec[Tel],
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {},
    step: Boolean = false,
    reset: Boolean = false
  ): Unit = {
    _poke(Option(data), last, strb, stai, endi, run, step, reset)
  }

  /**
   * Send an empty transfer (no valid data lanes). Unless overridden, a strobe of 0's is sent.
   * @param last Vector of `n` last `d`-bits values, one for each lane
   * @param strb Strobe value of `n` bits
   * @param stai Start index of lane validity, inclusive
   * @param endi End index of lane validity, inclusive
   * @param run Optional function to run after the poking
   * @param step Whether to step the clock after poking and executing `run`
   * @param reset Whether to reset the sink signals at the end, check the `reset` method. This usually only makes sense when stepping the clock.
   */
  def pokeEmpty(
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
    _poke(None, last, _strb, stai, endi, run, step, reset)
  }

  /**
   * A shorthand function poking the sink to enqueue a literal ''immediately'' in the first lane with the values
   * specified as the function arguments.<br>
   * Note that this function will '''always''' step the `clock` and reset the sink, check the `rest` function's documentation.
   * This is because the splatting disallows other keywords afterwards and before is inconvenient.
   * @param elems Tuples specifying bundle field values
   */
  def pokeEl(elems: (Tel => (Data, Data))*): Unit = {
    val litValue = elLit(elems: _*) // Use splat operator to propagate repeated parameters
    // Turn on all strobe lanes and limit stai and endi to the first element
    val strbValue = ((1 << sink.n) - 1).U
    pokeEl(litValue, strb = Some(strbValue), stai = Some(0.U), endi = Some(0.U), step = true, reset = true)
  }

  var renderer: Tel => String = _.toString()

  def printState: String = _printState(sink, renderer)

}

object SyncStreamDriver {
  def apply[Tel <: TydiEl, Tus <: Data](sink: PhysicalStreamDetailed[Tel, Tus], clockSig: Option[Clock] = None) = new SyncStreamDriver(sink, clockSig)
}
