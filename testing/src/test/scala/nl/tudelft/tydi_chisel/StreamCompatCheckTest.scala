package nl.tudelft.tydi_chisel

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class StreamCompatCheckTest extends AnyFlatSpec with ChiselScalatestTester {
  class MyBundle extends Group {
    val a: UInt = UInt(8.W)
    val b: Bool = Bool()
  }

  class MyBundle2 extends MyBundle

  class StreamConnectMod(
    in: PhysicalStream,
    out: PhysicalStream,
    typeCheckSelect: CompatCheck.Value = CompatCheck.Strict,
    errorReporting: CompatCheckResult.Value = CompatCheckResult.Error
  ) extends TydiModule {
    val inStream: PhysicalStream  = IO(Flipped(in))
    val outStream: PhysicalStream = IO(out)

    {
      implicit val typeCheckImplicit: CompatCheck.Value             = typeCheckSelect
      implicit val typeCheckResultImplicit: CompatCheckResult.Value = errorReporting
      outStream := inStream
    }
  }

  class DetailedStreamConnectMod[TIel <: TydiEl, TIus <: Data, TOel <: TydiEl, TOus <: Data](
    in: PhysicalStreamDetailed[TIel, TIus],
    out: PhysicalStreamDetailed[TOel, TOus],
    typeCheckSelect: CompatCheck.Value = CompatCheck.Strict
  ) extends TydiModule {
    val inStream: PhysicalStreamDetailed[TIel, TIus]  = IO(Flipped(in)).flip
    val outStream: PhysicalStreamDetailed[TOel, TOus] = IO(out)

    {
      implicit val typeCheckImplicit: CompatCheck.Value = typeCheckSelect
      outStream := inStream
    }
  }

  class DataBundle extends Bundle {
    val c: UInt = UInt(10.W)
    val d: Bool = Bool()
  }

  private val myBundleStream  = new PhysicalStreamDetailed(new MyBundle, c = 8)
  private val myBundle2Stream = new PhysicalStreamDetailed(new MyBundle2, c = 8)

  behavior of "Stream compatibility check"

  it should "check type" in {
    test(new DetailedStreamConnectMod(myBundleStream, myBundleStream)) { _ => }
    intercept[TydiStreamCompatException] {
      test(new DetailedStreamConnectMod(myBundleStream, myBundle2Stream)) { _ => }
    }
    intercept[TydiStreamCompatException] {
      test(new StreamConnectMod(PhysicalStream(new MyBundle, c = 1), PhysicalStream(new MyBundle2, c = 1))) { _ => }
    }
  }

  it should "weak check type" in {
    test(new DetailedStreamConnectMod(myBundleStream, myBundle2Stream, CompatCheck.Params)) { _ => }
    test(
      new StreamConnectMod(
        PhysicalStream(new MyBundle, c = 1),
        PhysicalStream(new MyBundle2, c = 1),
        CompatCheck.Params
      )
    ) { _ => }
  }

  it should "check parameters" in {
    val baseStream = PhysicalStream(new MyBundle, n = 1, d = 1, c = 1, new DataBundle)

    // Same parameters
    test(new StreamConnectMod(baseStream, PhysicalStream(new MyBundle, n = 1, d = 1, c = 1, new DataBundle))) { _ => }

    // Test unequal n parameter
    intercept[TydiStreamCompatException] {
      test(new StreamConnectMod(baseStream, PhysicalStream(new MyBundle, n = 2, d = 1, c = 1, new DataBundle))) { _ => }
    }

    // Test unequal d parameter
    intercept[TydiStreamCompatException] {
      test(new StreamConnectMod(baseStream, PhysicalStream(new MyBundle, n = 1, d = 2, c = 1, new DataBundle))) { _ => }
    }

    // Test c parameter inequality. Csink >= Csource is okay, else an exception is thrown.
    test(new StreamConnectMod(baseStream, PhysicalStream(new MyBundle, n = 1, d = 1, c = 7, new DataBundle))) { _ => }
    intercept[TydiStreamCompatException] {
      test(new StreamConnectMod(PhysicalStream(new MyBundle, n = 1, d = 2, c = 7, new DataBundle), baseStream)) { _ => }
    }
  }

  it should "print warnings" in {
    val baseStream = PhysicalStream(new MyBundle, n = 1, d = 1, c = 1, new DataBundle)

    // Create a module with warning output and give it incompatible streams.
    val stream = new java.io.ByteArrayOutputStream()
    Console.withErr(stream) {
      test(
        new StreamConnectMod(
          baseStream,
          PhysicalStream(new MyBundle, n = 2, d = 1, c = 1, new DataBundle),
          errorReporting = CompatCheckResult.Warning
        )
      ) { _ => }
    }
    // Check if error was written to console
    assert(stream.toString contains "Number of lanes between source and sink is not equal.")
  }
}
