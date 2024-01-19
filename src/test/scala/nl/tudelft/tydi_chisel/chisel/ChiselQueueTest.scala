package nl.tudelft.tydi_chisel.chisel

// Taken from https://github.com/ucb-bar/chiseltest/blob/main/src/test/scala/chiseltest/tests/QueueTest.scala
// and https://github.com/ucb-bar/chiseltest/blob/main/src/test/scala/chiseltest/tests/TestUtils.scala

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.AddVecLiteralConstructor
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class StaticModule[T <: Data](ioLit: T) extends Module {
  val out = IO(Output(chiselTypeOf(ioLit)))
  out := ioLit
}

class InputOnlyModule[T <: Data](ioType: T) extends Module {
  val in = IO(Input(ioType))
}

class PassthroughModule[T <: Data](ioType: T) extends Module {
  val in = IO(Input(ioType))
  val out = IO(Output(ioType))
  out := in
}

class PassthroughQueue[T <: Data](ioType: T) extends Module {
  val in = IO(Flipped(Decoupled(ioType)))
  val out = IO(Decoupled(ioType))
  out <> in
}

class ShifterModule[T <: Data](ioType: T, cycles: Int = 1) extends Module {
  val in = IO(Input(ioType))
  val out = IO(Output(ioType))
  out := ShiftRegister(in, cycles)
}

class QueueModule[T <: Data](ioType: T, entries: Int) extends Module {
  val in = IO(Flipped(Decoupled(ioType)))
  val out = IO(Decoupled(ioType))
  out <> Queue(in, entries)
}

class MyBundle extends Bundle {
  val a = UInt(8.W)
  val b = Bool()
}


class ChiselQueueTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Testers2 with Queue"

  it should "pass through a vector of bundles, using enqueueNow" in {
    val bundle = new MyBundle
    val myVec = Vec(2, bundle)

    test(new QueueModule(myVec, 2)) { c =>
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      val testVal = myVec.Lit(0 -> bundle.Lit(_.a -> 42.U, _.b -> true.B), 1 -> bundle.Lit(_.a -> 43.U, _.b -> false.B))
      val testVal2 = myVec.Lit(0 -> bundle.Lit(_.a -> 44.U, _.b -> false.B), 1 -> bundle.Lit(_.a -> 45.U, _.b -> true.B))

      c.out.expectInvalid()
      c.in.enqueueNow(testVal)
      parallel(
        c.out.expectDequeueNow(testVal),
        c.in.enqueueNow(testVal2)
      )
      c.out.expectDequeueNow(testVal2)
    }
  }

  it should "pass through a vector, using enqueueNow" in {
    val myVec = Vec(2, UInt(8.W))

    test(new QueueModule(myVec, 2)) { c =>
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      val testVal = myVec.Lit(0 -> 42.U, 1 -> 43.U)
      val testVal2 = myVec.Lit(0 -> 44.U, 1 -> 45.U)

      c.out.expectInvalid()
      c.in.enqueueNow(testVal)
      parallel(
        c.out.expectDequeueNow(testVal),
        c.in.enqueueNow(testVal2)
      )
      c.out.expectDequeueNow(testVal2)
    }
  }

  it should "pass through an aggregate, using enqueueNow" in {
    test(new QueueModule(new MyBundle, 2)) { c =>
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      val bundle = new MyBundle
      val testVal = bundle.Lit(_.a -> 42.U, _.b -> true.B)
      val testVal2 = bundle.Lit(_.a -> 43.U, _.b -> false.B)

      c.out.expectInvalid()
      c.in.enqueueNow(testVal)
      parallel(
        c.out.expectDequeueNow(testVal),
        c.in.enqueueNow(testVal2)
      )
      c.out.expectDequeueNow(testVal2)
    }
  }

  it should "pass through elements, using enqueueNow" in {
    test(new QueueModule(UInt(8.W), 2)) { c =>
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      c.out.expectInvalid()
      c.in.enqueueNow(42.U)
      parallel(
        c.out.expectDequeueNow(42.U),
        c.in.enqueueNow(43.U)
      )
      c.out.expectDequeueNow(43.U)
    }
  }

  it should "pass through elements, using enqueueSeq" in {
    test(new QueueModule(UInt(8.W), 2)) { c =>
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      fork {
        c.in.enqueueSeq(Seq(42.U, 43.U, 44.U))
      }

      c.out.expectInvalid()
      c.clock.step(1)  // wait for first element to enqueue
      c.out.expectDequeueNow(42.U)
      c.out.expectPeek(43.U)  // check that queue stalls
      c.clock.step(1)
      c.out.expectDequeueNow(43.U)
      c.out.expectDequeueNow(44.U)
      c.out.expectInvalid()
    }
  }

  it should "work with a combinational queue" in {
    test(new PassthroughQueue(UInt(8.W))) { c =>
      c.in.initSource()
      c.in.setSourceClock(c.clock)
      c.out.initSink()
      c.out.setSinkClock(c.clock)

      fork {
        c.in.enqueueSeq(Seq(42.U, 43.U, 44.U))
      }.fork {
        c.out.expectDequeueSeq(Seq(42.U, 43.U, 44.U))
      }.join()
    }
  }

  it should "work with IrrevocableIO" in{
    test(new Module{
      val io = IO(new Bundle{
        val in = Flipped(Irrevocable(UInt(8.W)))
        val out = Irrevocable(UInt(8.W))
      })
      io.out <> Queue(io.in)
    }){c =>
      c.io.in.initSource().setSourceClock(c.clock)
      c.io.out.initSink().setSinkClock(c.clock)
      parallel(
        c.io.in.enqueueSeq(Seq(5.U, 2.U)),
        c.io.out.expectDequeueSeq(Seq(5.U, 2.U))
      )
    }
  }

  it should "enqueue/dequeue zero-width data" in {
    test(new QueueModule(UInt(0.W), 2)) { c =>
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      fork {
        c.in.enqueueSeq(Seq(0.U, 0.U, 0.U))
      }

      c.out.expectInvalid()
      c.clock.step(1)  // wait for first element to enqueue
      c.out.expectDequeueNow(0.U)
      c.clock.step(1)
      c.out.expectDequeueNow(0.U)
      c.out.expectDequeueNow(0.U)
      c.out.expectInvalid()
    }
  }

}
