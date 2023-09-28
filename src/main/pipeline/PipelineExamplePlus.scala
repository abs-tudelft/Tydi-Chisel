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


// Operating at C=1
class MultiReducer(val n: Int) extends SubProcessorBase(new NumberGroup, new Stats, nIn=n, dIn = 1, dOut = 1) with PipelineTypes {
  val maxVal: BigInt = BigInt(Long.MaxValue)  // Must work with BigInt or we get an overflow

  val cMinReg: UInt = RegInit(maxVal.U(dataWidth))
  val cMaxReg: UInt = RegInit(0.U(dataWidth))
  val cSumReg: UInt = RegInit(0.U(dataWidth))
  val nSamplesReg: UInt = RegInit(0.U(dataWidth))
  val lastReg: Bool = RegInit(false.B)

  private val incomingItems = inStream.endi + 1.U(n.W)
  private val last: Bool = inStream.last(n-1)(0)
  private val strb: Bool = inStream.strb(0)

  lastReg := lastReg || last

  // Reset everything after transfer
  when (lastReg && outStream.ready) {
    cMinReg := maxVal.U(dataWidth)
    cMaxReg := 0.U(dataWidth)
    cSumReg := 0.U(dataWidth)
    nSamplesReg := 0.U
    lastReg := false.B
  }

  // Do work when we have a valid transfer
  when (inStream.valid && inStream.ready && strb) {
    nSamplesReg := nSamplesReg + incomingItems

    val values: Vec[UInt] = VecInit(inStream.data.zipWithIndex.map {
      case (el, i) => Mux(i.U <= inStream.endi, el.value.asUInt, 0.U)
    })

    cMaxReg := cMaxReg max values.reduceTree(_ max _)
    cSumReg := cSumReg + values.reduceTree(_ + _)

    cMinReg := cMinReg min VecInit(inStream.data.zipWithIndex.map {
      case (el, i) => Mux(i.U <= inStream.endi, el.value.asUInt, maxVal.U(dataWidth))
    }).reduceTree(_ min _)
  }

  inStream.ready := !lastReg
  outStream.valid := lastReg
  outStream.last(0) := lastReg
  outStream.el.sum := cSumReg
  outStream.el.min := cMinReg
  outStream.el.max := cMaxReg
  outStream.el.average := Mux(nSamplesReg > 0.U, cSumReg/nSamplesReg, 0.U)
  outStream.stai := 0.U
  outStream.endi := 1.U
  outStream.strb := 1.U
}

class MultiNonNegativeFilter extends MultiProcessorGeneral(
  Definition(new NonNegativeFilter), 4, new NumberGroup, new NumberGroup, d=1
)

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

class PipelinePlusModule(n: Int = 4, bufferSize: Int = 50)  extends SimpleProcessorBase(
  new NumberGroup, new Stats, nIn = n, nOut = 1, cIn = 7, cOut = 1, dIn = 1, dOut = 1) {
  out := in.processWith(new MultiNonNegativeFilter)
           .convert(bufferSize)
           .processWith(new MultiReducer(n))
}

class PipelinePlusStart extends SimpleProcessorBase(
  new NumberGroup, new NumberGroup, nIn = 4, nOut = 4, cIn = 7, cOut = 7, dIn = 1, dOut = 1) {
  out := in.processWith(new MultiNonNegativeFilter).convert(20)
}

object PipelineExamplePlus extends App {
  println("Test123")

//  test(new PipelinePlusModule()) { c =>
//    println(c.tydiCode)
//  }

//  println(emitCHIRRTL(new MultiNonNegativeFilter()))
//  println(emitSystemVerilog(new NonNegativeFilter(), firtoolOpts = firNormalOpts))

//  println(emitCHIRRTL(new MultiReducer()))
//  println(emitSystemVerilog(new Reducer(), firtoolOpts = firNormalOpts))

  println(emitCHIRRTL(new PipelinePlusModule()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new PipelinePlusModule(), firtoolOpts = firNormalOpts))

  println("Done")
}
