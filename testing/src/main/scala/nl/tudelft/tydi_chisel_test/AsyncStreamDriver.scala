package nl.tudelft.tydi_chisel_test

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.{AddObjectLiteralConstructor, AddVecLiteralConstructor}
import chisel3.simulator.PeekPokeAPI._
import nl.tudelft.tydi_chisel.{PhysicalStreamDetailed, TydiEl}

class AsyncStreamDriver[Tel <: TydiEl, Tus <: Data](sink: PhysicalStreamDetailed[Tel, Tus])
      extends StreamMetaUtil[Tel, Tus] {
  private var remainingData = List.empty[(Seq[Option[Tel]], Option[Tus])]

  def enqueueData(data: Seq[Seq[Tel]]): Unit = {
    remainingData = data.map(el => (el.map(Some(_)), None)).toList
  }

  private val n = sink.n

  def elLit(elems: (Tel => (Data, Data))*): Tel = {
    // Must use datatype instead of just .data or .el because Lit does not accept hardware types.
    // Use splat operator to propagate repeated parameters
    sink.getDataType.Lit(elems: _*)
  }

  def tick(): Unit = {
    if (remainingData.nonEmpty) {
      // If we have data to send, assert `valid` and write data to bus.
      sink.valid.poke(true)
      val dataPacket = remainingData.head._1

      var strbData = 0
      0 until n foreach { i =>
        val lanePacket = if (i < dataPacket.length) dataPacket(i) else None
        if (lanePacket.isDefined) {
          sink.data(i).poke(lanePacket.get)
          strbData = (strbData << 1) + 1
        } else {
//          sink.data(i).poke(0)
          strbData = strbData << 1
        }
        sink.strb.poke(strbData)
      }

      val userPacket = remainingData.head._2
      if (userPacket.isDefined) {
        sink.user.poke(userPacket.get)
      } else {
//        sink.user.poke(0.U)
      }

      // When the sink is also ready, we remove the element we sent.
      if (sink.ready.peek().litToBoolean) {
        remainingData = remainingData.tail
      }
    } else {
      // If we run out of data, `valid` is de-asserted.
      sink.valid.poke(false)
    }
  }

  def isDone: Boolean = remainingData.isEmpty

  var renderer: Tel => String = _.toString()

  def printState: String = _printState(sink, renderer)

}

object AsyncStreamDriver {
  def apply[Tel <: TydiEl, Tus <: Data](sink: PhysicalStreamDetailed[Tel, Tus]) = new AsyncStreamDriver(sink)
}
