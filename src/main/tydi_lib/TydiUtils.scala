package tydi_lib

import chisel3._
import chisel3.experimental.hierarchy.{Definition, Instance, instantiable, public}
import chisel3.util.{Cat, PopCount, PriorityEncoder, log2Ceil}

/**
 * Component that can be used to convert a high complexity stream to a low complexity stream.
 * @param template Physical stream to use as a reference for the input stream and partially the output stream.
 * @param memSize Size of the buffer in terms of total items/lanes.
 */
class ComplexityConverter(val template: PhysicalStream, val memSize: Int) extends SubProcessorSignalDef {
  // Get some information from the template
  private val elWidth = template.elWidth
  private val n = template.n
  private val d = template.d
  private val elType: TydiEl = template.getDataType
  // Create in- and output IO streams based on template
  override val in: PhysicalStream = IO(Flipped(PhysicalStream(elType, n, d = d, c = template.c)))
  override val out: PhysicalStream = IO(PhysicalStream(elType, n, d = d, c = 1))

  in.user := DontCare
  out.user := DontCare

  /** How many bits are required to represent an index of memSize */
  val indexSize: Int = log2Ceil(memSize)
  /** Stores index to write new data to in the register */
  val currentWriteIndex: UInt = RegInit(0.U(indexSize.W))
  val lastWidth: Int = d  // Assuming c = 7 here, or that this is the case for all complexities. Todo: Should still verify that.

  // Create actual element storage
  val dataReg: Vec[UInt] = Reg(Vec(memSize, UInt(elWidth.W)))
  val lastReg: Vec[UInt] = Reg(Vec(memSize, UInt(lastWidth.W)))
  /** How many elements/lanes are being transferred *out* this cycle */
  val transferOutItemCount: UInt = Wire(UInt(indexSize.W))

  // Shift the whole register file by `transferCount` places by default
  dataReg.zipWithIndex.foreach { case (r, i) =>
    r := dataReg(i.U + transferOutItemCount)
  }
  lastReg.zipWithIndex.foreach { case (r, i) =>
    r := lastReg(i.U + transferOutItemCount)
  }

  /** Signal for storing the indexes the current incoming lanes should write to */
  val writeIndexes: Vec[UInt] = Wire(Vec(n, UInt(indexSize.W)))
  // Split incoming data and last signals into indexable vectors
  private val lanesInSeq: Seq[UInt] = Seq.tabulate(n)(i => in.data((i+1)*elWidth-1, i*elWidth))
  private val lastInSeq: Seq[UInt] = Seq.tabulate(n)(i => in.last((i+1)*lastWidth-1, i*lastWidth))
  val lanesIn: Vec[UInt] = VecInit(lanesInSeq)
  val lastsIn: Vec[UInt] = VecInit(lastInSeq)

  /** Register that stores how many first dimension data-series are stored */
  val seriesStored: UInt = RegInit(0.U(indexSize.W))

  // Calculate & set write indexes
  writeIndexes.zipWithIndex.foreach({ case (indexWire, i) => {
    // Count which index this lane should get
    // The strobe bit adds 1 for each item, which is why we can remove 1 here, or we would not fill the first slot.
    indexWire := currentWriteIndex + PopCount(in.strb(i, 0)) - 1.U
    val isValid = in.strb(i) && in.valid
    when(isValid) {
      dataReg(indexWire) := lanesIn(i)
      lastReg(indexWire) := lastsIn(i)
    }
  }
  })

  // Index for new cycle is the one after the last index of last cycle - how many lanes we shifted out
  when (in.valid) {
    currentWriteIndex := writeIndexes.last + 1.U - transferOutItemCount
  } otherwise {
    currentWriteIndex := currentWriteIndex - transferOutItemCount
  }

  in.ready := currentWriteIndex < (memSize-n).U  // We are ready as long as we have enough space left for a full transfer

  transferOutItemCount := 0.U  // Default, overwritten below

  val storedData: Vec[UInt] = VecInit(dataReg.slice(0, n))
  val storedLasts: Vec[UInt] = VecInit(lastReg.slice(0, n))
  var outItemsReadyCount: UInt = Wire(UInt(indexSize.W))

  /** Stores the contents of the least significant bits */
  // The extra true concatenation is to fix the undefined PriorityEncoder behaviour when everything is 0
  val leastSignificantLasts: Seq[Bool] = Seq(false.B) ++ storedLasts.map(_(lastWidth - 1)) ++ Seq(true.B)
  val leastSignificantLastSignal: UInt = leastSignificantLasts.map(_.asUInt).reduce(Cat(_, _))
  // Todo: Check orientation
  val temp: UInt = PriorityEncoder(leastSignificantLasts)
  outItemsReadyCount := Mux(temp > n.U, n.U, temp)

  // Series transferred is the number of last lanes with high MSB
  val transferOutSeriesCount: UInt = Wire(UInt())
  transferOutSeriesCount := 0.U
  val transferInSeriesCount: UInt = lastsIn.map(_(0, 0) & in.ready).reduce(_ + _)

  // When we have at least one series stored and sink is ready
  when (seriesStored > 0.U) {
    when (out.ready) {
      // When transferLength is 0 (no last found) it means the end will come later, transfer n items
      transferOutItemCount := outItemsReadyCount

      // Series transferred is the number of last lanes with high MSB
      transferOutSeriesCount := storedLasts.map(_(0, 0)).reduce(_ + _)
    }

    // Set out stream signals
    out.valid := true.B
    out.data := storedData.reduce(Cat(_, _))  // Re-concatenate all the data lanes
    out.endi := transferOutItemCount - 1.U  // Encodes the index of the last valid lane.
    out.strb := (1.U << transferOutItemCount) - 1.U
    // This should be okay since you cannot have an end to a higher dimension without an end to a lower dimension first
    out.last := storedLasts(outItemsReadyCount)
  } .otherwise {
    out.valid := false.B
    out.last := DontCare
    out.endi := DontCare
    out.strb := DontCare
    out.data := DontCare

  }

  seriesStored := seriesStored + transferInSeriesCount - transferOutSeriesCount

  out.stai := 0.U

}

class TydiTestWrapper[Tinel <: TydiEl, Toutel <: TydiEl, Tinus <: TydiEl, Toutus <: TydiEl]
(module: => SubProcessorSignalDef, val eIn: Tinel, eOut: Toutel, val uIn: Tinus = Null(), val uOut: Toutus = Null()) extends TydiModule {
  val mod: SubProcessorSignalDef = Module(module)
  private val out_ref = mod.out
  private val in_ref = mod.in
  val out: PhysicalStreamDetailed[Toutel, Toutus] = IO(new PhysicalStreamDetailed(eOut, out_ref.n, out_ref.d, out_ref.c, r=false, uOut))
  val in: PhysicalStreamDetailed[Tinel, Tinus] = IO(Flipped(new PhysicalStreamDetailed(eIn, in_ref.n, in_ref.d, in_ref.c, r=true, uIn)))

  out := mod.out
  mod.in := in
}

class TydiProcessorTestWrapper[Tinel <: TydiEl, Toutel <: TydiEl, Tinus <: TydiEl, Toutus <: TydiEl]
(module: => SubProcessorBase[Tinel, Toutel, Tinus, Toutus]) extends TydiModule {
  val mod: SubProcessorBase[Tinel, Toutel, Tinus, Toutus] = Module(module)
  private val out_ref = mod.out
  private val in_ref = mod.in
  val out: PhysicalStreamDetailed[Toutel, Toutus] = IO(new PhysicalStreamDetailed(mod.eOut, out_ref.n, out_ref.d, out_ref.c, r = false, mod.uOut))
  val in: PhysicalStreamDetailed[Tinel, Tinus] = IO(Flipped(new PhysicalStreamDetailed(mod.eIn, in_ref.n, in_ref.d, in_ref.c, r = true, mod.uIn)))

  out := mod.out
  mod.in := in
}

/**
 * Base definition for SubProcessor that only includes signal definitions used in [[MultiProcessorGeneral]]
 */
@instantiable
abstract class SubProcessorSignalDef extends TydiModule {
  // Declare streams
  @public val out: PhysicalStream
  @public val in: PhysicalStream
}

/**
 * Basis of a SubProcessor definition that already includes the stream definitions and some base connections.
 * @param eIn Element type to use for input stream
 * @param eOut Element type to use for output stream
 * @param uIn Element type to use for input stream's user signals
 * @param uOut Element type to use for output stream's user signals
 * @tparam Tinel Element type of the input stream
 * @tparam Toutel Element type of the output stream
 * @tparam Tinus Element type of the input stream's user signals
 * @tparam Toutus Element type of the output stream's user signals
 */
@instantiable
abstract class SubProcessorBase[Tinel <: TydiEl, Toutel <: TydiEl, Tinus <: TydiEl, Toutus <: TydiEl]
(val eIn: Tinel, val eOut: Toutel, val uIn: Tinus = Null(), val uOut: Toutus = Null()) extends SubProcessorSignalDef {
  // Declare streams
  val outStream: PhysicalStreamDetailed[Toutel, Toutus] = PhysicalStreamDetailed(eOut, n = 1, d = 0, c = 1, r = false, u=uOut)
  val inStream: PhysicalStreamDetailed[Tinel, Tinus] = PhysicalStreamDetailed(eIn, n = 1, d = 0, c = 1, r = true, u=uIn)
  val out: PhysicalStream = outStream.toPhysical
  val in: PhysicalStream = inStream.toPhysical

  // Connect streams
  outStream := inStream

  // Set static signals
  outStream.strb := 1.U
  outStream.stai := 0.U
  outStream.endi := 0.U
  // stai and endi are 0-length
}

/**
 * Basis of a SubProcessor definition that already includes the stream definitions and some base connections.
 * @param eIn Element type to use for input stream
 * @param eOut Element type to use for output stream
 * @param uIn Element type to use for input stream's user signals
 * @param uOut Element type to use for output stream's user signals
 */
@instantiable
abstract class SimpleProcessorBase(val eIn: TydiEl, eOut: TydiEl, val uIn: Data = Null(), val uOut: Data = Null()) extends SubProcessorSignalDef {
  // Declare streams
  val out: PhysicalStream = IO(PhysicalStream(eOut, n = 1, d = 0, c = 1, u=uOut))
  val in: PhysicalStream = IO(Flipped(PhysicalStream(eIn, n = 1, d = 0, c = 1, u=uIn)))

  // Connect streams
  out := in

  // Set static signals
  out.strb := 1.U
  out.stai := 0.U
  out.endi := 0.U
}

/**
 * A MIMO processor that divides work over multiple sub-processors.
 * @param processorDef Definition of sub-processor
 * @param n Number of lanes/sub-processors
 * @param eIn Element type to use for input stream
 * @param eOut Element type to use for output stream
 * @param usIn Element type to use for input stream's `user` signals. Each sub-processor receives the same `user` signals.
 * @param usOut Element type to use for output stream's `user` signals. The output is set by the first sub-processor's `user` signals.
 */
class MultiProcessorGeneral(val processorDef: Definition[SubProcessorSignalDef], val n: Int = 6, val eIn: TydiEl, val eOut: TydiEl, val usIn: Data = Null(), val usOut: Data = Null()) extends TydiModule {
  val in: PhysicalStream = IO(Flipped(PhysicalStream(eIn, n=n, d=0, c=7, u=usIn)))
  val out: PhysicalStream = IO(PhysicalStream(eOut, n=n, d=0, c=7, u=usOut))

  val elSize: Int = eIn.getWidth

  out.valid := true.B
  out.last := 0.U
  out.stai := 0.U
  out.endi := 0.U

  private val subProcessors = for (i <- 0 until n) yield {
    val processor: Instance[SubProcessorSignalDef] = Instance(processorDef)
    //    val processor: SubProcessor = Module(new SubProcessor)
    processor.in.strb := 1.U  // Static signal
    processor.in.stai := 0.U  // Static signal
    processor.in.endi := 0.U  // Static signal
    processor.in.valid := in.strb(i)  // Sub input is valid when lane is valid
    processor.in.last := DontCare
    processor.in.data := in.data((elSize*i+1)-1, elSize*i)  // Set data
    processor.in.user := in.user
    processor.out.ready := out.ready
    processor
  }

  out.user := subProcessors(0).out.user

  private val inputReadies = subProcessors.map(_.in.ready)
  in.ready := inputReadies.reduce(_&&_)  // Top input is ready when all the modules are ready

  // Top lane is valid when sub is ready Todo: factor in out ready here
  out.strb := subProcessors.map(_.out.strb).reduce(Cat(_,_))
  // Re-concat all processor output data
  out.data := subProcessors.map(_.out.data).reduce(Cat(_, _))
}
