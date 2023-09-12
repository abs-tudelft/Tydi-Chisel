package tydi_lib.utils

import chisel3.util.{Cat, PopCount, PriorityEncoder, log2Ceil}
import chisel3._
import tydi_lib.{PhysicalStream, SubProcessorSignalDef, TydiEl}

/**
 * Component that can be used to convert a high complexity stream to a low complexity stream.
 *
 * @param template Physical stream to use as a reference for the input stream and partially the output stream.
 * @param memSize  Size of the buffer in terms of total items/lanes.
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
  val lastWidth: Int = d // Assuming c = 7 here, or that this is the case for all complexities. Todo: Should still verify that.

  // Create actual element storage
  val dataReg: Vec[UInt] = Reg(Vec(memSize, UInt(elWidth.W)))
  val lastReg: Vec[UInt] = Reg(Vec(memSize, UInt(lastWidth.W)))
  val emptyReg: Vec[Bool] = Reg(Vec(memSize, Bool()))
  /** How many elements/lanes are being transferred *out* this cycle */
  val transferOutItemCount: UInt = Wire(UInt(indexSize.W))

  // Shift the whole register file by `transferCount` places by default
  dataReg.zipWithIndex.foreach { case (r, i) =>
    r := dataReg(i.U + transferOutItemCount)
  }
  lastReg.zipWithIndex.foreach { case (r, i) =>
    r := lastReg(i.U + transferOutItemCount)
  }
  emptyReg.zipWithIndex.foreach { case (r, i) =>
    r := emptyReg(i.U + transferOutItemCount)
  }

  /** Signal for storing the indexes the current incoming lanes should write to */
  val writeIndexes: Vec[UInt] = Wire(Vec(n, UInt(indexSize.W)))
  //  val relativeIndexes: Vec[UInt] = Wire(Vec(n, UInt(indexSize.W)))
  // Split incoming data and last signals into indexable vectors
  // Todo: check orientation
  val lanesIn: Vec[UInt] = VecInit.tabulate(n)(i => in.data((i + 1) * elWidth - 1, i * elWidth))
  val lastsIn: Vec[UInt] = VecInit.tabulate(n)(i => in.last((i + 1) * lastWidth - 1, i * lastWidth))

  /** Register that stores how many first dimension data-series are stored */
  val seriesStored: UInt = RegInit(0.U(indexSize.W))

  val lastSeqProcessor: LastSeqProcessor = LastSeqProcessor(lastsIn)
  lastSeqProcessor.dataAt := in.laneValidityVec
  val prevReducedLast: UInt = RegInit(0.U(d.W))
  prevReducedLast := lastSeqProcessor.reducedLasts.last
  lastSeqProcessor.prevReduced := prevReducedLast

  val incrementIndexAt: UInt = in.laneValidity | lastSeqProcessor.outCheck
  val relativeIndexes: Vec[UInt] = VecInit.tabulate(n)(i => PopCount(incrementIndexAt(i, 0)))

  val writeIndexBase: UInt = currentWriteIndex - transferOutItemCount

  // Calculate & set write indexes
  for ((indexWire, i) <- writeIndexes.zipWithIndex) {
    // Count which index this lane should get
    // The strobe bit adds 1 for each item, which is why we can remove 1 here, or we would not fill the first slot.
    indexWire := writeIndexBase + relativeIndexes(i) - 1.U
    // Empty is if the a new sequence is assigned by last bits, but the lane is not valid
    val isEmpty: Bool = lastSeqProcessor.outCheck(i) && !in.laneValidity(i)
    val isValid = incrementIndexAt(i) && in.valid
    when (isValid) {
      dataReg(indexWire) := lanesIn(i)
      // It should get the reduced lasts of the lane *before* the *next* valid item
      when (indexWire > 0.U) {
        lastReg(indexWire - 1.U) := (if (i == 0) prevReducedLast else lastSeqProcessor.reducedLasts(i - 1))
      }
      emptyReg(indexWire) := isEmpty
    }
  }
  // Fix for the "looking back" way of setting last signals.
  lastReg(writeIndexes.last) := lastSeqProcessor.reducedLasts.last

  // Index for new cycle is the one after the last index of last cycle - how many lanes we shifted out
  when(in.valid) {
    currentWriteIndex := writeIndexes.last + 1.U
  } otherwise {
    currentWriteIndex := currentWriteIndex
  }

  in.ready := currentWriteIndex < (memSize - n).U // We are ready as long as we have enough space left for a full transfer

  transferOutItemCount := 0.U // Default, overwritten below

  val storedData: Vec[UInt] = VecInit(dataReg.slice(0, n))
  val storedLasts: Vec[UInt] = VecInit(lastReg.slice(0, n))
  //  val storedEmpty: Vec[UInt] = VecInit(emptyReg.slice(0, n))
  var outItemsReadyCount: UInt = Wire(UInt(indexSize.W))

  /** Stores the contents of the least significant bits */
  // The extra true concatenation is to fix the undefined PriorityEncoder behaviour when everything is 0
  val leastSignificantLasts: Seq[Bool] = Seq(false.B) ++ storedLasts.map(_(0)) ++ Seq(true.B)
  val leastSignificantLastSignal: UInt = leastSignificantLasts.map(_.asUInt).reduce(Cat(_, _))
  // Todo: Check orientation
  val innerSeqLength: UInt = PriorityEncoder(leastSignificantLasts)
  outItemsReadyCount := Mux(!emptyReg(0),
                            Mux(innerSeqLength > n.U, n.U, innerSeqLength),
                            1.U)

  // Series transferred is the number of last lanes with high MSB
  val transferOutSeriesCount: UInt = Wire(UInt(1.W))
  transferOutSeriesCount := 0.U
  private val msbIndex = Math.max(0, d-1)
  val transferInSeriesCount: UInt = Mux(in.ready, PopCount(lastsIn.map(_(msbIndex))), 0.U)

  seriesStored := seriesStored + transferInSeriesCount - transferOutSeriesCount

  // When we have at least one series stored and sink is ready
  when(seriesStored > 0.U) {
    when(out.ready) {
      // When transferLength is 0 (no last found) it means the end will come later, transfer n items
      transferOutItemCount := outItemsReadyCount

      // Series transferred is the number of last lanes with high MSB
      transferOutSeriesCount := storedLasts(out.endi)(msbIndex, msbIndex)
    }

    // Set out stream signals
    out.valid := true.B
    out.data := storedData.reduce((a, b) => Cat(b, a)) // Re-concatenate all the data lanes
    out.endi := outItemsReadyCount - 1.U // Encodes the index of the last valid lane.
    // If there is an empty item/seq, it will for sure be the first one at C=1, else it would not be empty
    when(!emptyReg(0)) {
      out.strb := (1.U << outItemsReadyCount) - 1.U
    } otherwise {
      out.strb := 0.U
    }
    // This should be okay since you cannot have an end to a higher dimension without an end to a lower dimension first
    out.last := storedLasts(out.endi) << ((n-1)*lastWidth)
  }.otherwise {
    out.valid := false.B
    out.last := DontCare
    out.endi := DontCare
    out.strb := DontCare
    out.data := DontCare
  }

  out.stai := 0.U

}
