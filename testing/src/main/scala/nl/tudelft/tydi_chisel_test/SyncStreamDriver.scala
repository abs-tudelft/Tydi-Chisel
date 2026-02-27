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

  def reset(): SyncStreamDriver.this.type = initWithSink()

  def elLit(elems: (Tel => (Data, Data))*): Tel = {
    // Must use datatype instead of just .data or .el because Lit does not accept hardware types.
    // Use splat operator to propagate repeated parameters
    sink.getDataType.Lit(elems: _*)
  }

  def dataLit(elems: (Int, Tel)*): Vec[Tel] = {
    Vec(sink.n, sink.getDataType).Lit(elems: _*)
  }

  def lastLit(elems: (Int, UInt)*): Vec[UInt] = {
    Vec(sink.n, UInt(sink.d.W)).Lit(elems: _*)
  }

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
    sink.valid.poke(true)
    run
    if (step) {
      if (clockSig.isDefined) {
        clockSig.get.step(1)
      }
    }
    if (reset) { this.reset() }
  }

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
      Option(Vec(sink.n, UInt(sink.d.W)).Lit(0 -> last.get))
    } else {
      None
    }
    _poke(Option(dataLit(0 -> data)), lastLit, strb, stai, endi, run, step, reset)
  }

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

  /** Send an empty transfer (no valid data lanes). Unless overridden, a strobe of 0's is sent. */
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

  def pokeEl(elems: (Tel => (Data, Data))*): Unit = {
    val litValue = elLit(elems: _*) // Use splat operator to propagate repeated parameters
    // Turn on all strobe lanes and limit stai and endi to the first element
    val strbValue = ((1 << sink.n) - 1).U
    pokeEl(litValue, strb = Some(strbValue), stai = Some(0.U), endi = Some(0.U))
  }

  var renderer: Tel => String = _.toString()

  def printState: String = _printState(sink, renderer)

}

object SyncStreamDriver {
  def apply[Tel <: TydiEl, Tus <: Data](sink: PhysicalStreamDetailed[Tel, Tus], clockSig: Option[Clock] = None) = new SyncStreamDriver(sink, clockSig)
}
