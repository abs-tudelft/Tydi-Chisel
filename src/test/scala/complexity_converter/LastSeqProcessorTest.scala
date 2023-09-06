package complexity_converter

import chisel3._
import chisel3.experimental.VecLiterals.AddObjectLiteralConstructor
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import tydi_lib.testing.printUtils.{binaryFromUint, printVecBinary}
import tydi_lib.utils.LastSeqProcessor

class LastSeqProcessorTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "LastSeqProcessor"

  it should "work with the testVec" in {

    test(new LastSeqProcessor(10, 3)) { c =>
      val testVec: Vec[UInt] = Vec.Lit(
        0.U, 1.U, 2.U, 0.U, 1.U, 0.U, 6.U, 0.U, 2.U, 4.U
      )
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
    }
  }
}
