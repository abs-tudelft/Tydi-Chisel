package nl.tudelft.tydi_chisel.examples.pipeline

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.{AddObjectLiteralConstructor, AddVecLiteralConstructor}
import chiseltest._
import nl.tudelft.tydi_chisel.Conversions._
import nl.tudelft.tydi_chisel.{TydiProcessorTestWrapper, TydiTestWrapper}
import org.scalatest.flatspec.AnyFlatSpec

class PipelineExamplePlusTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "PipelineExamplePlus"

  private val n: Int = 4

  class NonNegativeFilterWrap extends TydiTestWrapper(new MultiNonNegativeFilter, new NumberGroup, new NumberGroup)
  class ReducerWrap           extends TydiProcessorTestWrapper(new MultiReducer(n))
  class PipelineWrap          extends TydiTestWrapper(new PipelinePlusModule, new NumberGroup, new Stats)
  class PipelineStartWrap     extends TydiTestWrapper(new PipelinePlusStart, new NumberGroup, new NumberGroup)

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

      val t1     = vecLitFromSeq(Seq(3, 6, 9, 28))
      val t1Last = Vec.Lit(0.U, 0.U, 0.U, 1.U)

      c.in.enqueueNow(t1, endi = Some(2.U), last = Some(t1Last))
      println(c.out.printState(statsRenderer))
      c.clock.step()
      println(c.out.printState(statsRenderer))
      c.out.expectDequeueNow(_.min -> 3.U, _.max -> 9.U, _.sum -> 18.U, _.average -> 6.U)

      println(c.out.printState(statsRenderer))

      val t2     = vecLitFromSeq(Seq(18, 6, 9, 28))
      val t2Last = Vec.Lit(0.U, 0.U, 0.U, 0.U)
      val t3     = vecLitFromSeq(Seq(3, 10, 12, 0))
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

  it should "process a sequence in the first half" in {
    test(new PipelineStartWrap) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      val t1     = vecLitFromSeq(Seq(-3, 6, 9, 28))
      val t1Last = Vec.Lit(0.U, 0.U, 0.U, 1.U)

      parallel(
        c.in.enqueueNow(t1, endi = Some(2.U), last = Some(t1Last)),
        fork {
          fork
            .withRegion(Monitor) {
              println(c.in.printState(numRenderer))
              println(c.out.printState(numRenderer))
            }
            .joinAndStep(c.clock)
        }
      )
      c.clock.step()
      println(c.out.printState(numRenderer))
    }
  }

  it should "process a sequence" in {
    test(new PipelineWrap) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      val t1     = vecLitFromSeq(Seq(-3, 6, 9, 28))
      val t1Last = Vec.Lit(0.U, 0.U, 0.U, 1.U)

      parallel(
        c.in.enqueueNow(t1, endi = Some(2.U), last = Some(t1Last)),
        fork {
          fork
            .withRegion(Monitor) {
              println(c.in.printState(numRenderer))
              println(c.out.printState(statsRenderer))
            }
            .joinAndStep(c.clock)
        }
      )
      c.clock.step()
      println(c.out.printState(statsRenderer))
    }
  }

  case class StatsOb(
    count: BigInt = 0,
    min: BigInt = Long.MaxValue,
    max: BigInt = 0,
    sum: BigInt = 0,
    average: BigInt = 0
  )

  def randomSeq(n: Int): Seq[BigInt] = {
    Seq.fill(n)(Int.MinValue + BigInt(32, scala.util.Random))
  }

  def processSeq(seq: Seq[BigInt]): StatsOb = {
    val filtered = seq.filter(_ >= 0)
    val sum      = filtered.sum
    StatsOb(count = filtered.length, min = filtered.min, max = filtered.max, sum = sum, average = sum / filtered.size)
  }

  it should "process a sequence in parallel" in {
    test(new PipelineWrap) { c =>
      // Initialize signals
      c.in.initSource().setSourceClock(c.clock)
      c.out.initSink().setSinkClock(c.clock)

      // define min and max values numbers are allowed to have
      val rangeMin = BigInt(Long.MinValue)
      val rangeMax = BigInt(Long.MaxValue)
      val nNumbers = 50

      // Generate list of random numbers
      val nums     = randomSeq(nNumbers)
      val stats    = processSeq(nums)
      val filtered = nums.filter(_ >= 0)

      println(s"Number of filtered items: ${stats.count}")
      println(s"Stats: $stats")

      // Test component
      parallel(
        {
          for (elems <- nums.grouped(4)) {
            c.in.enqueueNow(vecLitFromSeq(elems), endi = Some((elems.length - 1).U))
          }
          c.in.enqueueEmptyNow(last = Some(c.in.lastLit(0 -> 1.U)))
        }, {
          c.out.waitForValid()
          println(c.out.printState(statsRenderer))
          c.out.expectDequeue(
            _.min     -> stats.min.U,
            _.max     -> stats.max.U,
            _.sum     -> stats.sum.U,
            _.average -> stats.average.U
          )
        }
      )
    }
  }
}
