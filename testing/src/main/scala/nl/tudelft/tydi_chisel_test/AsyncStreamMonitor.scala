package nl.tudelft.tydi_chisel_test

import chisel3.Data
import chisel3.simulator.PeekPokeAPI._
import nl.tudelft.tydi_chisel.{PhysicalStreamDetailed, TydiEl}

class AsyncStreamMonitor[Tel <: TydiEl, Tus <: Data](source: PhysicalStreamDetailed[Tel, Tus]) {
  private val _received = scala.collection.mutable.ListBuffer[(Seq[Option[BigInt]], BigInt)]()

  def received: Seq[(Seq[Option[BigInt]], BigInt)] = _received.toSeq

  private val n = source.n

  def tick(): Unit = {
    // Always be ready to receive
    source.ready.poke(true)

    if (source.valid.peek().litToBoolean) {
      val dataValues = 0 until n map { i =>
        val stai = source.stai.peek().litValue
        val endi = source.endi.peek().litValue
        val strb = source.strb(i).peek().litToBoolean

        // If the lane is valid, we read its value and put it in a `Some`
        if (strb && i >= stai && i <= endi) {
          Some(source.data(i).peekValue().asBigInt)
        } else {
          None
        }
      }
      val userValue                               = source.user.peekValue().asBigInt
      val rowValue: (Seq[Option[BigInt]], BigInt) = (dataValues, userValue)
      _received += rowValue
    }
  }
}
