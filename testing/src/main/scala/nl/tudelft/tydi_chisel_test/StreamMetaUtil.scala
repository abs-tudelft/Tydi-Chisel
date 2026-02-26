package nl.tudelft.tydi_chisel_test

import chisel3._
import chisel3.simulator.PeekPokeAPI._
import nl.tudelft.tydi_chisel.{PhysicalStreamDetailed, TydiEl}

trait StreamMetaUtil[Tel <: TydiEl, Tus <: Data] {
  def _printState(x: PhysicalStreamDetailed[Tel, Tus], renderer: Tel => String = _.toString()): String = {
    import printUtils2._
    val stringBuilder = new StringBuilder

    val out                                = '↑'
    val in                                 = '↓'
    def logicSymbol(cond: Boolean): String = if (cond) "✔" else "✖"
    val streamDir                          = if (x.r) in else out
    val streamAntiDir                      = if (x.r) out else in

    stringBuilder.append(s"State of \"${x.instanceName}\" $streamDir:\n")
    // Valid and ready signals
    stringBuilder.append(s"valid $streamDir: ${logicSymbol(x.valid.peek().litToBoolean)}\t\t\t")
    stringBuilder.append(s"ready $streamAntiDir: ${logicSymbol(x.ready.peek().litToBoolean)}\n")
    // Stai and endi signals
    if (x.n > 1) {
      stringBuilder.append(s"stai ≥: ${x.stai.peek().litValue}\t\t\t")
      stringBuilder.append(s"endi ≤: ${x.endi.peek().litValue}\n")
    }

    // Strobe signal
    if (x.c < 8) {
      // For C<8 all `strb` bits should be the same
      stringBuilder.append(s"strb: ${x.strb.peekValue().asBigInt.testBit(0)} (${binaryFromUint(x.strb.peek())})\n")
    } else {
      stringBuilder.append(s"strb: ${binaryFromUint(x.strb.peek())}\n")
    }
    // Last signal
    if (x.d == 0) {
      stringBuilder.append("last: -\n")
    } else if (x.c < 8) {
      stringBuilder.append(s"last: ${binaryFromUint(x.last.last.peek(), empty = "-")}\n")
    } else {
      stringBuilder.append(s"last: ${x.last.map(_.peek()).map(binaryFromUint(_)).mkString("|")}\n")
    }

    // Lane-specific info
    stringBuilder.append("Lanes:\n")
    x.data.zipWithIndex.foreach { case (lane, index) =>
      // Print data
      val dataString = renderer(lane.peek())
      stringBuilder.append(s"$index\tdata: $dataString\n")

      // Last signal for this lane
      if (x.c >= 8) {
        stringBuilder.append(s"\tlast: ${binaryFromUint(x.last(index).peek(), empty = "-")}\n")
      }

      // See if a lane is active or not and why
      val active_strobe = x.strb.peekValue().asBigInt.testBit(index)
      val active_stai   = if (x.n > 1) index >= x.stai.peek().litValue else true
      val active_endi   = if (x.n > 1) index <= x.endi.peek().litValue else true
      val active        = active_strobe && active_stai && active_endi
      stringBuilder.append(
        s"\tactive: ${logicSymbol(active)} \t\t(strb=${logicSymbol(active_strobe)}; stai=${logicSymbol(active_stai)}; endi=${logicSymbol(active_endi)};)\n"
      )
    }

    stringBuilder.toString
  }
}

object printUtils2 {
  def printVecBinary[T <: Data](vec: Vec[T]): String = {
    vec.map(v => binaryFromData(v)).mkString(", ")
  }

  def printVec[T <: Data](vec: Vec[T]): String = {
    vec.map(v => v.litValue).mkString(", ")
  }

  def binaryFromData[T <: Data](num: T, width: Option[Int] = None, empty: String = ""): String =
    binaryFromUint(num.asUInt, width, empty)

  def binaryFromUint(num: UInt, width: Option[Int] = None, empty: String = ""): String =
    binaryFromInt(num.litValue.toInt, width.getOrElse(num.getWidth), empty)

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
