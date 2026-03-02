package nl.tudelft.tydi_chisel.examples.pipeline

import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.experimental.VecLiterals.{AddObjectLiteralConstructor, AddVecLiteralConstructor}
import nl.tudelft.tydi_chisel.{TydiProcessorTestWrapper, TydiTestWrapper}
import nl.tudelft.tydi_chisel_test.Conversions._
import nl.tudelft.tydi_chisel_test.{SyncStreamDriver, SyncStreamMonitor}
import org.scalatest.flatspec.AnyFlatSpec

class PipelineExamplePlusChiselSim extends AnyFlatSpec with ChiselSim {
  behavior of "PipelineExamplePlus"

  private val n: Int = 4

  class NonNegativeFilterWrap extends TydiTestWrapper(new MultiNonNegativeFilter, new NumberGroup, new NumberGroup)
  class ReducerWrap           extends TydiProcessorTestWrapper(new MultiReducer(n))
  class PipelineWrap          extends TydiTestWrapper(new PipelinePlusModule, new NumberGroup, new Stats)
  class PipelineStartWrap     extends TydiTestWrapper(new PipelinePlusStart, new NumberGroup, new NumberGroup)

  private val numberGroup = new NumberGroup

  def vecLitFromSeq(s: Seq[BigInt]): Vec[NumberGroup] = {
    val mapping = s.map(c => numberGroup.Lit(_.value -> c.S, _.time -> 0.U)).zipWithIndex.map(v => (v._2, v._1))
    // I used to set the vec length to `n`, but this will create DontCare values that the poke function cannot handle.
    Vec(s.length, numberGroup).Lit(mapping: _*)
  }

  def numRenderer(c: NumberGroup): String = {
    s"${c.value.litValue} @ ${c.time.litValue}"
  }

  def statsRenderer(c: Stats): String = {
    s"min: ${c.min.litValue}, max: ${c.max.litValue}, sum: ${c.sum.litValue}, av: ${c.average.litValue}"
  }

  it should "reduce" in {
    simulate(new ReducerWrap) { c =>
      // Initialize signals
      val driver  = SyncStreamDriver(c.in, Some(c.clock))
      val monitor = SyncStreamMonitor(c.out)
      monitor.renderer = statsRenderer

      // First, we insert the sequence [3, 6, 9, 28], in one transfer, but use endi to disable the 28.
      // This will result in min = 3, max = 9, sum = 3+6+9 = 18 and average = 18/3 = 6.
      // Because a last signal is asserted for the highest lane, the reducer will reset after the transfer.
      val t1     = vecLitFromSeq(Seq(3, 6, 9, 28))
      val t1Last = Vec.Lit(0.U, 0.U, 0.U, 1.U)

      driver.poke(t1, endi = Some(2.U), last = Some(t1Last), run = c.clock.step(), reset = true)
      println(monitor.printState)
      // As we do not assert `ready` here, the result stays the same
      c.clock.step()
      println(monitor.printState)
      // Here we "consume" the output, so the output print next cycle will show reset values
      monitor.expect(_.min -> 3.U, _.max -> 9.U, _.sum -> 18.U, _.average -> 6.U)

      c.clock.step()
      monitor.reset() // De-assert ready
      println(monitor.printState)

      // Here we send [18, 6, 9, 28, 3, 10, 12, 0] in two transfers.
      // This results in min = 0, max = 28, sum = 18+6+9+28+3+10+12 = 86, average = 86/8 = 10
      val t2     = vecLitFromSeq(Seq(18, 6, 9, 28))
      val t2Last = Vec.Lit(0.U, 0.U, 0.U, 0.U)
      val t3     = vecLitFromSeq(Seq(3, 10, 12, 0))
      val t3Last = Vec.Lit(0.U, 0.U, 0.U, 1.U)

      driver.poke(t2, endi = Some(3.U), last = Some(t2Last), run = c.clock.step(), reset = true)
      println(monitor.printState)
      // The sequence is not finished yet, so the output should be invalid
      monitor.expectInvalid()
      driver.poke(t3, endi = Some(3.U), last = Some(t3Last), run = c.clock.step(), reset = true)
      println(monitor.printState)
      c.clock.step()
      println(monitor.printState)
      monitor.expect(_.min -> 0.U, _.max -> 28.U, _.sum -> 86.U, _.average -> 10.U)
    }
  }

  it should "process a sequence in the first half" in {
    simulate(new PipelineStartWrap) { c =>
      // Initialize signals
      val driver = SyncStreamDriver(c.in)
      driver.renderer = numRenderer
      val monitor = SyncStreamMonitor(c.out)
      monitor.renderer = numRenderer

      val t1 = vecLitFromSeq(Seq(-3, 6, 9, 28))
      val t1Last = Vec.Lit(0.U, 0.U, 0.U, 1.U)

      driver.poke(t1, endi = Some(2.U), last = Some(t1Last))
      println(driver.printState)
      println(monitor.printState)
      c.clock.step()
      driver.reset()
      println(monitor.printState)
    }
  }

  it should "process a sequence" in {
    simulate(new PipelineWrap) { c =>
      // Initialize signals
      val driver  = SyncStreamDriver(c.in)
      driver.renderer = numRenderer
      val monitor = SyncStreamMonitor(c.out)

      val t1     = vecLitFromSeq(Seq(-3, 6, 9, 28))
      val t1Last = Vec.Lit(0.U, 0.U, 0.U, 1.U)


      driver.poke(t1, endi = Some(2.U), last = Some(t1Last))
      println(driver.printState)
      println(monitor.printState)
      c.clock.step()
      driver.reset()
      println(monitor.printState)
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
    simulate(new PipelineWrap) { c =>
      // Initialize signals
      val driver  = SyncStreamDriver(c.in, Some(c.clock))
      val monitor = SyncStreamMonitor(c.out, Some(c.clock))

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
      for (elems <- nums.grouped(4)) {
        driver.poke(vecLitFromSeq(elems), endi = Some((elems.length - 1).U), step = true, reset = true)
      }
      driver.pokeEmpty(last = Some(driver.lastLit(0 -> 1.U)), step = true, reset = true)

      monitor.waitForValid()
      println(monitor.printState)
      monitor.expect(
        _.min     -> stats.min.U,
        _.max     -> stats.max.U,
        _.sum     -> stats.sum.U,
        _.average -> stats.average.U
      )
    }
  }
}
