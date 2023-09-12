package complexity_converter

import chisel3._
import chisel3.experimental.VecLiterals.AddObjectLiteralConstructor
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import tydi_lib.testing.printUtils.{binaryFromUint, printVecBinary}
import tydi_lib.utils.LastSeqProcessor

class LastSeqProcessorTest extends AnyFlatSpec with ChiselScalatestTester {
  def UIntVec(elems: Int*): Vec[UInt] = {
    Vec.Lit(elems.map(_.U): _*)
  }

  behavior of "LastSeqProcessor"

  it should "work with the testVec" in {

    test(new LastSeqProcessor(10, 3)) { c =>
      val testVec: Vec[UInt] = UIntVec(0, 1, 2, 0, 1, 0, 6, 0, 2, 4)
      c.lasts.poke(testVec)
      c.prevReduced.poke(0.U)
      print("prevReduced:  ")
      println(binaryFromUint(c.prevReduced.peek()))
      print("Input:        ")
      println(printVecBinary(c.lasts.peek()))
      print("reducedLasts: ")
      println(printVecBinary(c.reducedLasts.peek()))
      print("outCheck:       ")
      println(binaryFromUint(c.outCheck.peek()).reverse.mkString(",   "))

      print("outIndex: ")
      println(c.outIndex.peek().litValue)

      c.reducedLasts.expect(UIntVec(0, 1, 3, 3, 1, 1, 7, 7, 2, 6))
      c.outCheck.expect("b0100010000".U)
    }
  }
}
