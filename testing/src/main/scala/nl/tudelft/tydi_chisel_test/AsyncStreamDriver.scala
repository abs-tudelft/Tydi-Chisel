package nl.tudelft.tydi_chisel_test

import chisel3._
import chisel3.simulator.PeekPokeAPI._
import nl.tudelft.tydi_chisel.{PhysicalStreamDetailed, TydiEl}

class AsyncStreamDriver[Tel <: TydiEl, Tus <: Data](sink: PhysicalStreamDetailed[Tel, Tus]) {
  private var remainingData = List.empty[(Seq[Option[Tel]], Option[Tus])]

  def enqueueData(data: Seq[Seq[Tel]]): Unit = {
    remainingData = data.map(el => (el.map(Some(_)), None)).toList
  }

  private val n = sink.n

  def tick(): Unit = {
    if (remainingData.nonEmpty) {
      // If we have data to send, assert `valid` and write data to bus.
      sink.valid.poke(true)
      val dataPacket = remainingData.head._1

      0 until n foreach { i =>
        val lanePacket = if (i < dataPacket.length) dataPacket(i) else None
        if (lanePacket.isDefined) {
          sink.data(i).poke(lanePacket.get)
          sink.strb(i).poke(true)
        } else {
          sink.data(i).poke(0)
          sink.strb(i).poke(false)
        }
      }

      val userPacket = remainingData.head._2
      if (userPacket.isDefined) {
        sink.user.poke(userPacket.get)
      } else {
        sink.user.poke(0)
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

}
