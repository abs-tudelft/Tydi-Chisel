package TydiTesting

import org.scalatest._
import chisel3._
import chiseltest._
import chisel3.util._
import org.scalatest.flatspec.AnyFlatSpec
import tydi_lib._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.AddVecLiteralConstructor
import tydi_lib.testing.Conversions._

class SimplePassthroughModule[T <: TydiEl](ioType: T) extends SubProcessorBase(ioType, ioType)

class TydiPassthroughModule[T <: TydiEl](ioType: T) extends TydiModule {
  val mod = Module(new SimplePassthroughModule(ioType))
  val out = IO(new PhysicalStreamDetailed(ioType, c=7, r=true))
  val in = IO(Flipped(new PhysicalStreamDetailed(ioType, c=7)))
  mod.in := in
  mod.out := out
}

//class QueueModule[T <: TydiEl](ioType: T, entries: Int) extends SubProcessorSignalDef {
//  val out: PhysicalStream = IO(PhysicalStream(ioType))
//  val in: PhysicalStream = IO(PhysicalStream(ioType))
//  out <> Queue(in, entries)
//}

class MyBundle extends Group {
  val a = UInt(8.W)
  val b = Bool()
}

class TydiStreamDriverTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Testers2 with Queue"

  it should "pass through an aggregate" in {
    test(new TydiPassthroughModule(new MyBundle)) { c =>
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      val bundle = new MyBundle
      val testVal = bundle.Lit(_.a -> 42.U, _.b -> true.B)
      val testVal2 = c.in.dataLit(_.a -> 43.U, _.b -> false.B)

      c.out.expectInvalid()
      c.in.enqueueNow(testVal)
      parallel(
        c.out.expectDequeueNow(testVal),
        c.in.enqueueNow(testVal2)
      )
      c.out.expectDequeueNow(testVal2)
    }
  }
}
