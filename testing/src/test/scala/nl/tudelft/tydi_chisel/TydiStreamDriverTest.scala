package nl.tudelft.tydi_chisel

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chiseltest._
import nl.tudelft.tydi_chisel_test.Conversions._
import org.scalatest.flatspec.AnyFlatSpec

class TydiStreamDriverTest extends AnyFlatSpec with ChiselScalatestTester {
  class MyBundle extends Group {
    val a = UInt(8.W)
    val b = Bool()
  }

  class SimplePassthroughModule[T <: TydiEl](ioType: T) extends SubProcessorBase(ioType, ioType)

  class TydiPassthroughModule[T <: TydiEl](ioType: T) extends TydiModule {
    //  val mod = Module(new SimplePassthroughModule(ioType))
    val out = IO(new PhysicalStreamDetailed(ioType, c = 8))
    val in  = IO(Flipped(new PhysicalStreamDetailed(ioType, c = 7, r = true)))
    out := in
  }

  // class nl.tudelft.tydi_chisel.QueueModule[T <: TydiEl](ioType: T, entries: Int) extends SubProcessorSignalDef {
  //  val out: PhysicalStream = IO(PhysicalStream(ioType))
  //  val in: PhysicalStream = IO(PhysicalStream(ioType))
  //  out <> Queue(in, entries)
  // }

  behavior of "Testers2 with Queue"

  it should "pass through an aggregate" in {
    test(new TydiPassthroughModule(new MyBundle)) { c =>
      c.in.initSource()
      c.out.initSink()

      val whatever: Seq[MyBundle => (Data, Data)] = Seq(_.a -> 0.U, _.b -> false.B)

      val bundle   = new MyBundle
      val testVal  = bundle.Lit(_.a -> 42.U, _.b -> true.B)
      val testVal2 = c.in.elLit(_.a -> 43.U, _.b -> false.B)

      c.out.expectInvalid()
      c.clock.step()
      parallel({
        c.in.enqueueElNow(testVal)
        c.in.enqueueElNow(_.a -> 43.U, _.b -> false.B)
      }, {
        c.out.expectDequeueNow(_.a -> 42.U, _.b -> true.B)
        c.out.expectDequeueNow(testVal2)
      })
    }
  }
}
