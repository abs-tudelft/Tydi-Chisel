package nl.tudelft.tydi_chisel

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chiseltest._
import nl.tudelft.tydi_chisel_test.Conversions._
import org.scalatest.flatspec.AnyFlatSpec

class StreamCompatCheckTest extends AnyFlatSpec with ChiselScalatestTester {
  class MyBundle extends Group {
    val a: UInt = UInt(8.W)
    val b: Bool = Bool()
  }

  class MyBundle2 extends MyBundle

  class StreamConnectMod(in: PhysicalStream, out: PhysicalStream) extends TydiModule {
    val inStream: PhysicalStream  = IO(Flipped(in))
    val outStream: PhysicalStream = IO(out)
    outStream := inStream
  }

  class DetailedStreamConnectMod[TIel <: TydiEl, TIus <: Data, TOel <: TydiEl, TOus <: Data](
    in: PhysicalStreamDetailed[TIel, TIus],
    out: PhysicalStreamDetailed[TOel, TOus]
  ) extends TydiModule {
    val inStream: PhysicalStreamDetailed[TIel, TIus]  = IO(Flipped(in)).flip
    val outStream: PhysicalStreamDetailed[TOel, TOus] = IO(out)
    outStream := inStream
  }

  private val myBundleStream  = new PhysicalStreamDetailed(new MyBundle, c = 8)
  private val myBundle2Stream = new PhysicalStreamDetailed(new MyBundle2, c = 8)

  behavior of "Stream compatibility check"

  it should "check type" in {
    test(new DetailedStreamConnectMod(myBundleStream, myBundleStream)) { _ => }
    intercept[TydiStreamCompatException] {
      test(new DetailedStreamConnectMod(myBundleStream, myBundle2Stream)) { _ => }
    }
  }
}
