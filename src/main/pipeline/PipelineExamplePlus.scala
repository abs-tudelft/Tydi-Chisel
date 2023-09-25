package pipeline

import tydi_lib._
import chisel3._
import chisel3.experimental.hierarchy.Definition
import chisel3.internal.firrtl.Width
import chisel3.util.Counter
import chiseltest.RawTester.test
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

/*class NonNegativeFilter extends SubProcessorBase(new NumberGroup, new NumberGroup) {
  val filter: Bool = inStream.el.value >= 0.S && inStream.valid
//  outStream.valid := filter
  outStream.strb := filter

  inStream.ready := true.B
}*/

class MultiReducer extends SubProcessorBase(new NumberGroup, new Stats) with PipelineTypes {
  val maxVal: BigInt = BigInt(Long.MaxValue)  // Must work with BigInt or we get an overflow
  val cMin: UInt = RegInit(maxVal.U(dataWidth))
  val cMax: UInt = RegInit(0.U(dataWidth))
  val nValidSamples: Counter = Counter(Int.MaxValue)
  val nSamples: Counter = Counter(Int.MaxValue)
  val cSum: UInt = RegInit(0.U(dataWidth))

  inStream.ready := true.B
  outStream.valid := nSamples.value > 0.U

  when (inStream.valid) {
    val value = inStream.el.value.asUInt
    nSamples.inc()
    when (inStream.strb(0)) {
      cMin := cMin min value
      cMax := cMax max value
      cSum := cSum + value
      nValidSamples.inc()
    }
  }
  outStream.el.sum := cSum
  outStream.el.min := cMin
  outStream.el.max := cMax
  outStream.el.average := Mux(nValidSamples.value > 0.U, cSum/nValidSamples.value, 0.U)
}

class MultiNonNegativeFilter extends MultiProcessorGeneral(Definition(new NonNegativeFilter), 6, new NumberGroup, new NumberGroup, d=1)

/*class PipelinePlusModule extends TydiModule {
  private val numberGroup = new NumberGroup
  private val statsGroup = new Stats

  // Create and connect physical streams following standard with concatenated data bitvector
  val numsIn: PhysicalStream = IO(Flipped(PhysicalStream(numberGroup, 1, c = 8)))
  val statsOut: PhysicalStream = IO(PhysicalStream(statsGroup, 1, c = 8))

  val filter = Module(new MultiNonNegativeFilter())
  filter.in := numsIn
  val reducer = Module(new MultiReducer())
  reducer.in := filter.out
  statsOut := reducer.out
}*/

class PipelinePlusModule extends SimpleProcessorBase(new NumberGroup, new Stats) {
  out := in.processWith(new MultiNonNegativeFilter).processWith(new MultiReducer())
}

object PipelineExamplePlus extends App {
  println("Test123")

  test(new PipelinePlusModule()) { c =>
    println(c.tydiCode)
  }

//  println(emitCHIRRTL(new MultiNonNegativeFilter()))
//  println(emitSystemVerilog(new NonNegativeFilter(), firtoolOpts = firNormalOpts))

//  println(emitCHIRRTL(new MultiReducer()))
//  println(emitSystemVerilog(new Reducer(), firtoolOpts = firNormalOpts))

  println(emitCHIRRTL(new PipelinePlusModule()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new PipelinePlusModule(), firtoolOpts = firNormalOpts))

  println("Done")
}
