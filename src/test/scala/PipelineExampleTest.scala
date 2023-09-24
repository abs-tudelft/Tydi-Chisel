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
        c.in.enqueueElNow(_.time -> 123976.U, _.value -> 6.S),
        c.out.expectDequeueNow(_.time -> 123976.U, _.value -> 6.S),
      )

      parallel(
        c.in.enqueueElNow(_.time -> 123976.U, _.value -> 0.S),
        c.out.expectDequeueNow(_.time -> 123976.U, _.value -> 0.S),
      )

      parallel(
        c.in.enqueueElNow(_.time -> 123976.U, _.value -> -7.S),
        timescope {
          c.out.ready.poke(true.B)
          fork.withRegion(Monitor) {
            c.out.strb.expect(0.U)
          }.joinAndStep(c.clock)
        }
      )
    }
  }

  it should "reduce" in {
    test(new ReducerWrap) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      c.in.enqueueElNow(_.time -> 123976.U, _.value -> 6.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 6.U, _.sum -> 6.U, _.average -> 6.U)

      c.in.enqueueElNow(_.time -> 124718.U, _.value -> 12.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 12.U, _.sum -> 18.U, _.average -> 9.U)

      c.in.enqueueElNow(_.time -> 129976.U, _.value -> 15.S)
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
      c.in.enqueueElNow(_.time -> 123976.U, _.value -> 6.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 6.U, _.sum -> 6.U, _.average -> 6.U)

      // Enqueue second value that should be filtered out, output remains constant
      c.in.enqueueElNow(_.time -> 123976.U, _.value -> -6.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 6.U, _.sum -> 6.U, _.average -> 6.U)

      // Enqueue second valid value
      c.in.enqueueElNow(_.time -> 124718.U, _.value -> 12.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 12.U, _.sum -> 18.U, _.average -> 9.U)

      // Enqueue second invalid value
      c.in.enqueueElNow(_.time -> 124718.U, _.value -> -12.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 12.U, _.sum -> 18.U, _.average -> 9.U)

      // Enqueue third value
      c.in.enqueueElNow(_.time -> 129976.U, _.value -> 15.S)
      println(c.out.printState())
      c.out.expectDequeueNow(_.min -> 6.U, _.max -> 15.U, _.sum -> 33.U, _.average -> 11.U)
    }
  }

  it should "process a sequence in parallel" in {
    test(new PipelineWrap) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      // define min and max values numbers are allowed to have
      val rangeMin = BigInt(Long.MinValue)
      val rangeMax = BigInt(Long.MaxValue)
      val nNumbers = 100

      // Generate list of random numbers
      val nums = Seq.fill(nNumbers)(
        Int.MinValue + BigInt(32, scala.util.Random)
      )

      // println(nums)

      // Storage for statistics
      case class StatsOb(count: BigInt = 0,
                         min: BigInt = rangeMax,
                         max: BigInt = 0,
                         sum: BigInt = 0,
                         average: BigInt = 0)

      val initialStats = StatsOb()

      // Calculate cumulative statistics
      val statsSeq = nums.scanLeft(initialStats) { (s, num) =>
        if (num >= 0) {
          val newCount = s.count + 1
          val newSum = s.sum + num
          val newMin = s.min min num
          val newMax = s.max max num
          val newAverage = newSum / newCount

          s.copy(count = newCount, min = newMin, max = newMax, sum = newSum, average = newAverage)
        } else {
          s
        }
      }.tail

      // Test component
      parallel(
        {
          for ((elem, i) <- nums.zipWithIndex) {
            c.in.enqueueElNow(_.time -> i.U, _.value -> elem.S)
          }
        },
        {
          for ((elem, i) <- statsSeq.zipWithIndex) {
            // println(s"$i: $elem")
            c.out.expectDequeue(_.min -> elem.min.U, _.max -> elem.max.U, _.sum -> elem.sum.U, _.average -> elem.average.U)
          }
        }
      )
    }
  }
}
