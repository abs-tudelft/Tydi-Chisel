import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.{AddObjectLiteralConstructor, AddVecLiteralConstructor}
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import pipeline._
import tydi_lib._
import tydi_lib.testing.Conversions._

class PipelineExamplePlusTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "PipelineExamplePlus"

  private val n: Int = 4

  class NonNegativeFilterWrap extends TydiTestWrapper(new NonNegativeFilter, new NumberGroup, new NumberGroup)
  class ReducerWrap extends TydiProcessorTestWrapper(new MultiReducer(n))
  class PipelineWrap extends TydiTestWrapper(new PipelinePlusModule, new NumberGroup, new Stats)

  private val numberGroup = new NumberGroup

  def vecLitFromSeq(s: Seq[BigInt]): Vec[NumberGroup] = {
    val mapping = s.map(c => numberGroup.Lit(_.value -> c.S, _.time -> 0.U)).zipWithIndex.map(v => (v._2, v._1))
    Vec(n, numberGroup).Lit(mapping: _*)
  }

  def numRenderer(c: NumberGroup): String = {
    s"${c.value.litValue} @ ${c.time.litValue}"
  }

  def statsRenderer(c: Stats): String = {
    s"min: ${c.min.litValue}, max: ${c.max.litValue}, sum: ${c.sum.litValue}, av: ${c.average.litValue}"
  }

  it should "reduce" in {
    test(new ReducerWrap) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      val t1 = vecLitFromSeq(Seq(3, 6, 9, 28))
      val t1Last = Vec.Lit(0.U, 0.U, 0.U, 1.U)

      c.in.enqueueNow(t1, endi = Some(2.U), last = Some(t1Last))
      println(c.out.printState(statsRenderer))
      c.clock.step()
      println(c.out.printState(statsRenderer))
      c.out.expectDequeueNow(_.min -> 3.U, _.max -> 9.U, _.sum -> 18.U, _.average -> 6.U)

      println(c.out.printState(statsRenderer))

      val t2 = vecLitFromSeq(Seq(18, 6, 9, 28))
      val t2Last = Vec.Lit(0.U, 0.U, 0.U, 0.U)
      val t3 = vecLitFromSeq(Seq(3, 10, 12, 0))
      val t3Last = Vec.Lit(0.U, 0.U, 0.U, 1.U)

      c.clock.step()
      println(c.out.printState(statsRenderer))
      c.in.enqueueNow(t2, endi = Some(3.U), last = Some(t2Last))
      println(c.out.printState(statsRenderer))
      c.out.expectInvalid()
      c.in.enqueueNow(t3, endi = Some(3.U), last = Some(t3Last))
      println(c.out.printState(statsRenderer))
      c.clock.step()
      println(c.out.printState(statsRenderer))
      c.out.expectDequeueNow(_.min -> 0.U, _.max -> 28.U, _.sum -> 86.U, _.average -> 10.U)
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
