package nl.tudelft.tydi_chisel.examples.pipeline

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import nl.tudelft.tydi_chisel.{TydiProcessorTestWrapper, TydiTestWrapper}
import nl.tudelft.tydi_chisel_test.{SyncStreamDriver, SyncStreamMonitor}
import org.scalatest.flatspec.AnyFlatSpec

class PipelineExampleChiselSim extends AnyFlatSpec with ChiselSim {
  behavior of "PipelineExample"

  class NonNegativeFilterWrap extends TydiTestWrapper(new NonNegativeFilter, new NumberGroup, new NumberGroup)
  class ReducerWrap           extends TydiProcessorTestWrapper(new Reducer)
  class PipelineWrap          extends TydiTestWrapper(new PipelineExampleModule, new NumberGroup, new Stats)

  it should "filter negative values" in {
    simulate(new NonNegativeFilterWrap) { c =>
      // Initialize signals
      val driver  = SyncStreamDriver(c.in)
      val monitor = SyncStreamMonitor(c.out)

//      driver.enqueueElNow(_.time -> 123976.U, _.value -> 6.S)
      c.in.valid.poke(true.B)
      c.in.data(0).time.poke(123976.U)
      c.in.data(0).value.poke(6.S)
      monitor.expect(_.time -> 123976.U, _.value -> 6.S)
      c.clock.step()
      driver.enqueueElNow(_.time -> 123976.U, _.value -> 0.S)
      monitor.expect(_.time -> 123976.U, _.value -> 0.S)
      c.clock.step()
      driver.enqueueElNow(_.time -> 123976.U, _.value -> -7.S)
      monitor.expectEmpty(strb = Some(0.U))
    }
  }

  it should "reduce" in {
    simulate(new ReducerWrap) { c =>
      // Initialize signals
      val driver  = SyncStreamDriver(c.in)
      val monitor = SyncStreamMonitor(c.out)

      driver.enqueueElNow(_.time -> 123976.U, _.value -> 6.S)
      println(monitor.printState)
      monitor.expect(_.min -> 6.U, _.max -> 6.U, _.sum -> 6.U, _.average -> 6.U)

      driver.enqueueElNow(_.time -> 124718.U, _.value -> 12.S)
      println(monitor.printState)
      monitor.expect(_.min -> 6.U, _.max -> 12.U, _.sum -> 18.U, _.average -> 9.U)

      driver.enqueueElNow(_.time -> 129976.U, _.value -> 15.S)
      println(monitor.printState)
      monitor.expect(_.min -> 6.U, _.max -> 15.U, _.sum -> 33.U, _.average -> 11.U)
    }
  }

  /*it should "process a sequence" in {
    simulate(new PipelineWrap) { c =>
      // Initialize signals
      val driver  = SyncStreamDriver(c.in)
      val monitor = SyncStreamMonitor(c.out)

      // Enqueue first value
      driver.enqueueElNow(_.time -> 123976.U, _.value -> 6.S)
      println(monitor.printState)
      monitor.expect(_.min -> 6.U, _.max -> 6.U, _.sum -> 6.U, _.average -> 6.U)

      // Enqueue second value that should be filtered out, output remains constant
      driver.enqueueElNow(_.time -> 123976.U, _.value -> -6.S)
      println(monitor.printState)
      monitor.expect(_.min -> 6.U, _.max -> 6.U, _.sum -> 6.U, _.average -> 6.U)

      // Enqueue second valid value
      driver.enqueueElNow(_.time -> 124718.U, _.value -> 12.S)
      println(monitor.printState)
      monitor.expect(_.min -> 6.U, _.max -> 12.U, _.sum -> 18.U, _.average -> 9.U)

      // Enqueue second invalid value
      driver.enqueueElNow(_.time -> 124718.U, _.value -> -12.S)
      println(monitor.printState)
      monitor.expect(_.min -> 6.U, _.max -> 12.U, _.sum -> 18.U, _.average -> 9.U)

      // Enqueue third value
      driver.enqueueElNow(_.time -> 129976.U, _.value -> 15.S)
      println(monitor.printState)
      monitor.expect(_.min -> 6.U, _.max -> 15.U, _.sum -> 33.U, _.average -> 11.U)
    }
  }

  it should "process a sequence in parallel" in {
    simulate(new PipelineWrap) { c =>
      // Initialize signals
      val driver  = SyncStreamDriver(c.in)
      val monitor = SyncStreamMonitor(c.out)

      // define min and max values numbers are allowed to have
      val rangeMin = BigInt(Long.MinValue)
      val rangeMax = BigInt(Long.MaxValue)
      val nNumbers = 100

      // Generate list of random numbers
      val nums = Seq.fill(nNumbers)(Int.MinValue + BigInt(32, scala.util.Random))

      // println(nums)

      // Storage for statistics
      case class StatsOb(
        count: BigInt = 0,
        min: BigInt = rangeMax,
        max: BigInt = 0,
        sum: BigInt = 0,
        average: BigInt = 0
      )

      val initialStats = StatsOb()

      // Calculate cumulative statistics
      val statsSeq = nums
        .scanLeft(initialStats) { (s, num) =>
          if (num >= 0) {
            val newCount   = s.count + 1
            val newSum     = s.sum + num
            val newMin     = s.min min num
            val newMax     = s.max max num
            val newAverage = newSum / newCount

            s.copy(count = newCount, min = newMin, max = newMax, sum = newSum, average = newAverage)
          } else {
            s
          }
        }
        .tail

      // Test component
      parallel(
        {
          for ((elem, i) <- nums.zipWithIndex) {
            driver.enqueueElNow(_.time -> i.U, _.value -> elem.S)
          }
        }, {
          for ((elem, i) <- statsSeq.zipWithIndex) {
            // println(s"$i: $elem")
            c.out
              .expectDequeue(_.min -> elem.min.U, _.max -> elem.max.U, _.sum -> elem.sum.U, _.average -> elem.average.U)
          }
        }
      )
    }
  }*/
}
