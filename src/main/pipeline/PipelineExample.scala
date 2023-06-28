package pipeline

import tydi_lib._
import chisel3._
import chisel3.internal.firrtl.Width
import chisel3.util.Counter
import chiseltest.RawTester.test
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

trait PipelineTypes {
  val dataWidth: Width = 64.W
  def signedData: SInt = SInt(64.W)
  def unsingedData: UInt = UInt(64.W)
}

class NumberGroup extends Group with PipelineTypes {
  val time: UInt = UInt(64.W)
  val value: SInt = signedData
}

class Stats extends Group with PipelineTypes {
  val min: UInt = unsingedData
  val max: UInt = unsingedData
  val sum: UInt = unsingedData
  val average: UInt = unsingedData
}

class NumberModuleOut extends TydiModule {
  private val timestampedMessageBundle = new NumberGroup // Can also be inline

  // Create Tydi logical stream object
  val stream: PhysicalStreamDetailed[NumberGroup, Null] = PhysicalStreamDetailed(timestampedMessageBundle, 1, c = 7)

  // Create and connect physical streams following standard with concatenated data bitvector
  val tydi_port_top: PhysicalStream = stream.toPhysical

  // → Assign values to logical stream group elements directly
  stream.el.time := System.currentTimeMillis().U
  stream.el.value := -3457.U

  // → Assign some values to the other Tydi signals
  //   We have 1 lane in this case

  // Top stream
  stream.valid := true.B
  stream.strb := 1.U
  stream.stai := 0.U
  stream.endi := 1.U
  stream.last := 0.U
}

class NonNegativeFilter extends SubProcessorBase(new NumberGroup, new NumberGroup) with PipelineTypes {
  val filter: Bool = inStream.el.value >= 0.S(dataWidth)
  outStream.valid := filter
  outStream.strb := filter
//  outStream.data := inStream.data

  inStream.ready := true.B
  outStream.last := inStream.last
}

class Reducer extends SubProcessorBase(new NumberGroup, new Stats) with PipelineTypes {
  val cMin = RegInit(0.U(dataWidth))
  val maxVal = (1 << dataWidth.get)-1
  val cMax = RegInit(maxVal.U(dataWidth))
  val nSamples = Counter(maxVal)
  val cSum = RegInit(0.U(dataWidth))

  inStream.ready := true.B

  when (inStream.valid) {
    val value = inStream.el.value.asUInt
    cMin := cMin min value
    cMax := cMin max value
    cSum := cSum + value
    nSamples.inc()
  }
  outStream.el.sum := cSum
  outStream.el.min := cMin
  outStream.el.max := cMax
  outStream.el.average := cSum/nSamples.value
}

class NumberModuleIn extends TydiModule {
  val io1 = IO(Flipped(new PhysicalStream(new NumberGroup, n=1, d=2, c=7, u=new Null())))
  io1 :<= DontCare
  io1.ready := DontCare
}

class TopLevelModule extends TydiModule {
  private val numberGroup = new NumberGroup
  private val statsGroup = new Stats

  // Create and connect physical streams following standard with concatenated data bitvector
  val numsIn: PhysicalStream = IO(Flipped(PhysicalStream(numberGroup, 1, c = 7)))
  val statsOut: PhysicalStream = IO(PhysicalStream(statsGroup, 1, c = 7))

  val filter = Module(new NonNegativeFilter())
  filter.in :<>= numsIn
  val reducer = Module(new Reducer())
  reducer.in :<>= filter.out
  statsOut :<>= reducer.out
}

object PipelineExample extends App {
  println("Test123")

  test(new TopLevelModule()) { c =>
    println(c.tydiCode)
  }

  println(emitSystemVerilog(new NonNegativeFilter(), firtoolOpts = firNormalOpts))

  println(emitSystemVerilog(new Reducer(), firtoolOpts = firNormalOpts))

  println(emitCHIRRTL(new TopLevelModule()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new TopLevelModule(), firtoolOpts = firNormalOpts))

  println("Done")
}
