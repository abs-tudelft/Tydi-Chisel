package tydi_lib.utils

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.util.Counter
import tydi_lib._

class BinaryResult extends Group {
  val value: Bool = Bool()
}

class CharType extends BitsEl(8.W)

/**
 * Simple string comparator that takes in a Tydi stream of characters and outputs at the end of every sequence whether
 * the string matches.
 * @param compare String to compare to
 */
class StringComparator(compare: String) extends SubProcessorBase(new CharType, new BinaryResult) {
  private val charOb = new CharType

  val stringLit: Seq[Int] = compare.map(_.toInt)

  val n: Int = compare.length
//  val dataReg: CharType = Reg(charOb)
  val inputValid: Bool = in.laneValidity(0)
  val result: Bool = RegInit(true.B)
  val resultReady: Bool = RegInit(false.B)

  private val counter = Counter(n)
  val overflow: Bool = WireInit(false.B)

  when(inputValid) {
    overflow := counter.inc()

    for ((char, i) <- stringLit.zipWithIndex) {
      when(counter.value === i.U && inStream.el.value =/= char.U) {
        result := false.B
      }
    }
  }
  when(overflow) {
    result := false.B
  }

  outStream.valid := resultReady

  when (inStream.valid) {
    when (inStream.last(0)(0)) {
      outStream.el.value := Mux(counter.value =/= n.U, false.B, result)
      resultReady := true.B
    }
  }

  when (outStream.ready && resultReady) {
    counter.reset()
    result := true.B
    resultReady := false.B
  }
}
