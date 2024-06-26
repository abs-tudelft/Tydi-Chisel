package nl.tudelft.tydi_chisel.utils

import chisel3._
import chisel3.experimental.VecLiterals.AddObjectLiteralConstructor
import chiseltest._
import nl.tudelft.tydi_chisel_test.printUtils.binaryFromUint
import org.scalatest.flatspec.AnyFlatSpec

class LastSeqLengthTest extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "LastSeqLength"
  // test class body here

  it should "work with the testVec" in {

    test(new LastSeqLength(10, 3)) { c =>
      val testVec: Vec[UInt] = Vec.Lit(0.U, 1.U, 2.U, 0.U, 1.U, 0.U, 6.U, 0.U, 2.U, 4.U)
//      val testVec: Vec[UInt] = c.lasts.Lit(
//        0 -> 0.U, 1 -> 1.U, 2 -> 2.U, 3 -> 0.U, 4 -> 1.U, 5 -> 0.U, 6 -> 6.U, 7 -> 0.U, 8 -> 2.U, 9 -> 4.U
//      )
      c.lasts.poke(testVec)
      print("outCheck: ")
      println(binaryFromUint(c.outCheck.peek()))
      print("outIndex: ")
      println(c.outIndex.peek())
    }
  }
}
