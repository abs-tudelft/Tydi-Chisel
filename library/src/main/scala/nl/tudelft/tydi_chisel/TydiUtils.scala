package nl.tudelft.tydi_chisel

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Definition, Instance}
import chisel3.util.{log2Ceil, Cat, PopCount, PriorityEncoder}

class TydiTestWrapper[Tinel <: TydiEl, Toutel <: TydiEl, Tinus <: TydiEl, Toutus <: TydiEl](
  module: => SubProcessorSignalDef,
  val eIn: Tinel,
  eOut: Toutel,
  val uIn: Tinus = Null(),
  val uOut: Toutus = Null()
) extends TydiModule {
  val mod: SubProcessorSignalDef = Module(module)
  private val out_ref            = mod.out
  private val in_ref             = mod.in
  val out: PhysicalStreamDetailed[Toutel, Toutus] = IO(
    new PhysicalStreamDetailed(eOut, out_ref.n, out_ref.d, out_ref.c, r = false, uOut)
  )
  val in: PhysicalStreamDetailed[Tinel, Tinus] = IO(
    Flipped(new PhysicalStreamDetailed(eIn, in_ref.n, in_ref.d, in_ref.c, r = true, uIn))
  )

  out    := mod.out
  mod.in := in
}

class TydiProcessorTestWrapper[Tinel <: TydiEl, Toutel <: TydiEl, Tinus <: TydiEl, Toutus <: TydiEl](
  module: => SubProcessorBase[Tinel, Toutel, Tinus, Toutus]
) extends TydiModule {
  val mod: SubProcessorBase[Tinel, Toutel, Tinus, Toutus] = Module(module)
  private val out_ref                                     = mod.out
  private val in_ref                                      = mod.in
  val out: PhysicalStreamDetailed[Toutel, Toutus] = IO(
    new PhysicalStreamDetailed(mod.eOut, out_ref.n, out_ref.d, out_ref.c, r = false, mod.uOut)
  )
  val in: PhysicalStreamDetailed[Tinel, Tinus] = IO(
    Flipped(new PhysicalStreamDetailed(mod.eIn, in_ref.n, in_ref.d, in_ref.c, r = true, mod.uIn))
  )

  out    := mod.out
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
abstract class SubProcessorBase[Tinel <: TydiEl, Toutel <: TydiEl, Tinus <: TydiEl, Toutus <: TydiEl](
  val eIn: Tinel,
  val eOut: Toutel,
  val uIn: Tinus = Null(),
  val uOut: Toutus = Null(),
  nIn: Int = 1,
  dIn: Int = 0,
  cIn: Int = 1,
  nOut: Int = 1,
  dOut: Int = 0,
  cOut: Int = 1
) extends SubProcessorSignalDef {
  // Declare streams
  val outStream: PhysicalStreamDetailed[Toutel, Toutus] =
    PhysicalStreamDetailed(eOut, n = nOut, d = dOut, c = cOut, r = false, u = uOut)
  val inStream: PhysicalStreamDetailed[Tinel, Tinus] =
    PhysicalStreamDetailed(eIn, n = nIn, d = dIn, c = cIn, r = true, u = uIn)
  val out: PhysicalStream = outStream.toPhysical
  val in: PhysicalStream  = inStream.toPhysical

  // Connect streams
  if (eIn.typeName == eOut.typeName && nIn == nOut && dIn == dOut)
    outStream := inStream
}

/**
 * Basis of a SubProcessor definition that already includes the stream definitions and some base connections.
 * @param eIn Element type to use for input stream
 * @param eOut Element type to use for output stream
 * @param uIn Element type to use for input stream's user signals
 * @param uOut Element type to use for output stream's user signals
 */
@instantiable
abstract class SimpleProcessorBase(
  val eIn: TydiEl,
  eOut: TydiEl,
  val uIn: Data = Null(),
  val uOut: Data = Null(),
  nIn: Int = 1,
  dIn: Int = 0,
  cIn: Int = 1,
  nOut: Int = 1,
  dOut: Int = 0,
  cOut: Int = 1
) extends SubProcessorSignalDef {
  // Declare streams
  val out: PhysicalStream = IO(PhysicalStream(eOut, n = nOut, d = dOut, c = cOut, u = uOut))
  val in: PhysicalStream  = IO(Flipped(PhysicalStream(eIn, n = nIn, d = dIn, c = cIn, u = uIn)))

  // Connect streams
  if (eIn.getWidth == eOut.getWidth && nIn == nOut && dIn == dOut)
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
class MultiProcessorGeneral(
  val processorDef: Definition[SubProcessorSignalDef],
  val n: Int = 6,
  val eIn: TydiEl,
  val eOut: TydiEl,
  val usIn: Data = Null(),
  val usOut: Data = Null(),
  d: Int = 0
) extends SubProcessorSignalDef {
  val in: PhysicalStream  = IO(Flipped(PhysicalStream(eIn, n = n, d = d, c = 8, u = usIn)))
  val out: PhysicalStream = IO(PhysicalStream(eOut, n = n, d = d, c = 8, u = usOut))

  val elSize: Int = eIn.getWidth

  out.stai := in.stai
  out.endi := in.endi

  val inputValid: Bool      = Wire(Bool())
  val outputReady: Bool     = Wire(Bool())
  private val inputLastVec  = in.lastVec
  private val outputLastVec = out.lastVec

  private val subProcessors = for (i <- 0 until n) yield {
    val processor: Instance[SubProcessorSignalDef] = Instance(processorDef)
    //    val processor: SubProcessor = Module(new SubProcessor)
    processor.in.stai   := 0.U                                       // Static signal
    processor.in.endi   := 0.U                                       // Static signal
    processor.in.strb   := in.strb(i)
    processor.in.valid  := inputValid
    processor.in.last   := inputLastVec(i)
    processor.in.data   := in.data(elSize * (i + 1) - 1, elSize * i) // Set data
    processor.in.user   := in.user
    processor.out.ready := outputReady
    processor
  }

  out.user := subProcessors(0).out.user
  out.last := inputLastVec.asUInt // Todo, is there a better option here?

  private val inputReadies = subProcessors.map(_.in.ready)
  in.ready := inputReadies.reduce(_ && _) // Top input is ready when all the modules are ready
  inputValid := in.ready && in.valid // Wait for all processors to be ready before we transmit the valid signal to them

  private val outputValids = subProcessors.map(_.out.valid)
  out.valid := outputValids.reduce(_ && _) // Top output is valid when all the modules are valid
  outputReady := out.ready && out.valid // Wait for all processors to be valid before we transmit the ready signal to them

  // Top lane is valid when sub is ready
  out.strb := subProcessors.map(_.out.strb).reduce((a, b) => Cat(b, a))
  // Re-concat all processor output data
  out.data := subProcessors.map(_.out.data).reduce((a, b) => Cat(b, a))
}

/**
 * A stream-duplication component. One input stream is duplicated to `k` output streams.
 * @param k Number of streams to produce
 * @param template Stream to use as a template
 */
class StreamDuplicator(val k: Int = 2, template: PhysicalStream) extends TydiModule {
  // Create a new instance so we do not need to worry about directions.
  private val stream: PhysicalStream =
    PhysicalStream(template.getDataType, n = template.n, d = template.d, c = template.c, u = template.getUserType)
  val in: PhysicalStream       = IO(Flipped(stream))
  val out: Vec[PhysicalStream] = IO(Vec(k, stream))

  // Connect input to all the output streams
  out.foreach(_ := in)

  // Input is ready when all the output streams are ready
  in.ready := out.map(_.ready).reduce(_ && _)
  for (valid <- out.map(_.valid)) {
    // Set output streams valid when input is valid and all outputs are ready
    valid := in.valid && in.ready
  }
}

object StreamDuplicator {
  def apply(k: Int, template: PhysicalStream): Vec[PhysicalStream] = {
    val mod = Module(new StreamDuplicator(k, template))
    mod.out
  }
}
