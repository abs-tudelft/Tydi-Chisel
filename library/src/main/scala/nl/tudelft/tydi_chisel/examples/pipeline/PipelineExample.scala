package nl.tudelft.tydi_chisel.examples.pipeline

import chisel3._
import chisel3.util.Counter
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}
import nl.tudelft.tydi_chisel._

trait PipelineTypes {
  val dataWidth: Width   = 64.W
  def signedData: SInt   = SInt(dataWidth)
  def unsingedData: UInt = UInt(dataWidth)
}

class NumberGroup extends Group with PipelineTypes {
  val time: UInt  = UInt(64.W)
  val value: SInt = signedData
}

class Stats extends Group with PipelineTypes {
  val min: UInt     = unsingedData
  val max: UInt     = unsingedData
  val sum: UInt     = unsingedData
  val average: UInt = unsingedData
}

class NumberModuleOut extends TydiModule {
  private val timestampedMessageBundle = new NumberGroup // Can also be inline

  // Create Tydi logical stream object
  val stream: PhysicalStreamDetailed[NumberGroup, Null] = PhysicalStreamDetailed(timestampedMessageBundle, 1, c = 7)

  // Create and connect physical streams following standard with concatenated data bitvector
  val tydi_port_top: PhysicalStream = stream.toPhysical

  // → Assign values to logical stream group elements directly
  stream.el.time  := System.currentTimeMillis().U
  stream.el.value := -3457.U

  // → Assign some values to the other Tydi signals
  //   We have 1 lane in this case

  // Top stream
  stream.valid := true.B
  stream.strb  := 1.U
  stream.stai  := 0.U
  stream.endi  := 1.U
  stream.last  := 0.U
}

class NonNegativeFilter extends SubProcessorBase(new NumberGroup, new NumberGroup) {
  val filter: Bool = inStream.el.value >= 0.S && inStream.valid
//  outStream.valid := filter
  outStream.strb := inStream.strb(0) && filter

  inStream.ready := true.B
}

class Reducer extends SubProcessorBase(new NumberGroup, new Stats) with PipelineTypes {
  val maxVal: BigInt         = BigInt(Long.MaxValue) // Must work with BigInt or we get an overflow
  val cMin: UInt             = RegInit(maxVal.U(dataWidth))
  val cMax: UInt             = RegInit(0.U(dataWidth))
  val nValidSamples: Counter = Counter(Int.MaxValue)
  val nSamples: Counter      = Counter(Int.MaxValue)
  val cSum: UInt             = RegInit(0.U(dataWidth))

  inStream.ready  := true.B
  outStream.valid := nSamples.value > 0.U

  when(inStream.valid) {
    val value = inStream.el.value.asUInt
    nSamples.inc()
    when(inStream.strb(0)) {
      cMin := cMin min value
      cMax := cMax max value
      cSum := cSum + value
      nValidSamples.inc()
    }
  }

  outStream.last(0)    := inStream.last(0)
  outStream.el.sum     := cSum
  outStream.el.min     := cMin
  outStream.el.max     := cMax
  outStream.el.average := Mux(nValidSamples.value > 0.U, cSum / nValidSamples.value, 0.U)
  outStream.stai       := 0.U
  outStream.endi       := 1.U
  outStream.strb       := outStream.valid
}

class NumberModuleIn extends TydiModule {
  val io1 = IO(Flipped(new PhysicalStream(new NumberGroup, n = 1, d = 2, c = 7, u = new Null())))
  io1 :<= DontCare
  io1.ready := DontCare
}

/*class TopLevelModule extends TydiModule {
  private val numberGroup = new NumberGroup
  private val statsGroup = new Stats

  // Create and connect physical streams following standard with concatenated data bitvector
  val numsIn: PhysicalStream = IO(Flipped(PhysicalStream(numberGroup, 1, c = 7)))
  val statsOut: PhysicalStream = IO(PhysicalStream(statsGroup, 1, c = 7))

  val filter = Module(new NonNegativeFilter())
  filter.in := numsIn
  val reducer = Module(new Reducer())
  reducer.in := filter.out
  statsOut := reducer.out
}*/

class PipelineExampleModule extends SimpleProcessorBase(new NumberGroup, new Stats) {
  out := in.processWith(new NonNegativeFilter).processWith(new Reducer)
}

object PipelineExample extends App {
  println("Test123")

//  println(emitCHIRRTL(new NonNegativeFilter()))
//  println(emitSystemVerilog(new NonNegativeFilter(), firtoolOpts = firNormalOpts))

//  println(emitCHIRRTL(new Reducer()))
//  println(emitSystemVerilog(new Reducer(), firtoolOpts = firNormalOpts))

  println(emitCHIRRTL(new PipelineExampleModule()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new PipelineExampleModule(), firtoolOpts = firNormalOpts))

  println("Done")
}
