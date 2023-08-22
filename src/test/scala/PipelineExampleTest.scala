package TydiTesting

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import tydi_lib._
import tydi_lib.testing.Conversions._
import pipeline._

class PipelineExampleTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "PipelineExample"

  class NonNegativeFilterWrap extends TydiTestWrapper(new NonNegativeFilter, new NumberGroup, new NumberGroup)
  class ReducerWrap extends TydiProcessorTestWrapper(new Reducer)
  class PipelineWrap extends TydiTestWrapper(new TopLevelModule, new NumberGroup, new Stats)

  it should "filter negative values" in {
    test(new NonNegativeFilterWrap) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      parallel(
        c.in.enqueueNow(_.time -> 123976.U, _.value -> 6.S),
        c.out.expectDequeueNow(_.time -> 123976.U, _.value -> 6.S),
      )

      parallel(
        c.in.enqueueNow(_.time -> 123976.U, _.value -> 0.S),
        c.out.expectDequeueNow(_.time -> 123976.U, _.value -> 0.S),
      )

      parallel(
        c.in.enqueueNow(_.time -> 123976.U, _.value -> -7.S),
        c.out.expectInvalid(),
      )
    }
  }

  it should "reduce" in {
    test(new ReducerWrap) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      c.in.enqueueNow(_.time -> 123976.U, _.value -> 6.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 6.U, _.sum -> 6.U, _.average -> 6.U)

      c.in.enqueueNow(_.time -> 124718.U, _.value -> 12.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 12.U, _.sum -> 18.U, _.average -> 9.U)

      c.in.enqueueNow(_.time -> 129976.U, _.value -> 15.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 15.U, _.sum -> 33.U, _.average -> 11.U)
    }
  }

  it should "process a sequence" in {
    test(new PipelineWrap) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      // Enqueue first value
      c.in.enqueueNow(_.time -> 123976.U, _.value -> 6.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 6.U, _.sum -> 6.U, _.average -> 6.U)

      // Enqueue second value that should be filtered out, output remains constant
      c.in.enqueueNow(_.time -> 123976.U, _.value -> -6.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 6.U, _.sum -> 6.U, _.average -> 6.U)

      // Enqueue second valid value
      c.in.enqueueNow(_.time -> 124718.U, _.value -> 12.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 12.U, _.sum -> 18.U, _.average -> 9.U)

      // Enqueue second invalid value
      c.in.enqueueNow(_.time -> 124718.U, _.value -> -12.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 12.U, _.sum -> 18.U, _.average -> 9.U)

      // Enqueue third value
      c.in.enqueueNow(_.time -> 129976.U, _.value -> 15.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 15.U, _.sum -> 33.U, _.average -> 11.U)
    }
  }
}
