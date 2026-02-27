package nl.tudelft.tydi_chisel_test

import chisel3.Data
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.{AddObjectLiteralConstructor, AddVecLiteralConstructor}
import chisel3.simulator.PeekPokeAPI._
import nl.tudelft.tydi_chisel.{PhysicalStreamDetailed, TydiEl}

class AsyncStreamMonitor[Tel <: TydiEl, Tus <: Data](source: PhysicalStreamDetailed[Tel, Tus])
      extends StreamMetaUtil[Tel, Tus] {
  private val _received = scala.collection.mutable.ListBuffer[(Seq[Option[Tel]], Tus)]()

  def received: Seq[(Seq[Option[Tel]], Tus)] = _received.toSeq

  private val n = source.n

  def elLit(elems: (Tel => (Data, Data))*): Tel = {
    // Must use datatype instead of just .data or .el because Lit does not accept hardware types.
    // Use splat operator to propagate repeated parameters
    source.getDataType.Lit(elems: _*)
  }

  def tick(): Unit = {
    // Always be ready to receive
    source.ready.poke(true)

    if (source.valid.peek().litToBoolean) {
      val stai = if (n > 1) source.stai.peek().litValue else BigInt(0)
      val endi = if (n > 1) source.endi.peek().litValue else BigInt(n)
      val strb = source.strb.peek().litValue
      val dataValues = 0 until n map { i =>
        val strbBit = strb.testBit(i)
        // If the lane is valid, we read its value and put it in a `Some`
        if (strbBit && i >= stai && i <= endi) {
          Some(source.data(i).peek())
        } else {
          None
        }
      }
      val userValue                         = source.user.peek()
      val rowValue: (Seq[Option[Tel]], Tus) = (dataValues, userValue)
      _received += rowValue
    }
  }

  var renderer: Tel => String = _.toString()

  def printState: String = _printState(source, renderer)
}

object AsyncStreamMonitor {
  def apply[Tel <: TydiEl, Tus <: Data](source: PhysicalStreamDetailed[Tel, Tus]) = new AsyncStreamMonitor(source)
}
