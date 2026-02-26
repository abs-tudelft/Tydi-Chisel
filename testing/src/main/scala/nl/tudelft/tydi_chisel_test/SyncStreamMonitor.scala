package nl.tudelft.tydi_chisel_test

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.{AddObjectLiteralConstructor, AddVecLiteralConstructor}
import chisel3.simulator.PeekPokeAPI._
import nl.tudelft.tydi_chisel.{PhysicalStreamDetailed, TydiEl}

class SyncStreamMonitor[Tel <: TydiEl, Tus <: Data](source: PhysicalStreamDetailed[Tel, Tus]) {

  private val n = source.n

  private def initSource(): this.type = {
    source.valid.poke(false)
    if (n > 1) {
      source.stai.poke(0.U)
      source.endi.poke((source.n - 1).U)
    }
    source.strb.poke(((1 << source.n) - 1).U(source.n.W)) // Set strobe to all 1's
    if (source.d > 0) {
      val lasts: Seq[UInt] = Seq.fill(source.n)(0.U(source.d.W))
      source.last.poke(Vec.Lit(lasts: _*))
    }
    this
  }

  initSource()

  def elLit(elems: (Tel => (Data, Data))*): Tel = {
    // Must use datatype instead of just .data or .el because Lit does not accept hardware types.
    // Use splat operator to propagate repeated parameters
    source.getDataType.Lit(elems: _*)
  }

  def dataLit(elems: (Int, Tel)*): Vec[Tel] = {
    Vec(source.n, source.getDataType).Lit(elems: _*)
  }

  def lastLit(elems: (Int, UInt)*): Vec[UInt] = {
    Vec(source.n, UInt(source.d.W)).Lit(elems: _*)
  }

  private def _expect(
    data: Option[Tel],
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {}
  ): Unit = {
    source.ready.poke(true)
    source.valid.expect(true.B)
    run
    if (data.isDefined) {
      // Fixme uhg this requires an encoder ?
//      source.el.expect(data.get)
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
  }

  def expect(
    data: Tel,
    last: Option[Vec[UInt]] = None,
    strb: Option[UInt] = None,
    stai: Option[UInt] = None,
    endi: Option[UInt] = None,
    run: => Unit = {}
  ): Unit = {
    _expect(Option(data), last, strb, stai, endi, run)
  }

  /** Expect an empty transfer (no valid data lanes). Unless overridden, a strobe of 0's is expected. */
  def expectEmpty(
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
    _expect(None, last, _strb, stai, endi, run)
  }

  def expect(elems: (Tel => (Data, Data))*): Unit = {
    val litValue = elLit(elems: _*) // Use splat operator to propagate repeated parameters
    expect(litValue)
  }

  /*def expectPeek(data: Tel): Unit = {
    source.valid.expect(true.B)
    source.el.expect(data)
  }*/

  def expectInvalid(): Unit = {
    source.valid.expect(false.B)
  }

}

object SyncStreamMonitor {
  def apply[Tel <: TydiEl, Tus <: Data](source: PhysicalStreamDetailed[Tel, Tus]) = new SyncStreamMonitor(source)
}
