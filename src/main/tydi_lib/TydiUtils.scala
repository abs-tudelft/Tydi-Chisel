package tydi_lib

import chisel3._
import chisel3.experimental.hierarchy.{Definition, Instance, instantiable, public}
import chisel3.util.{Cat, PopCount, PriorityEncoder, log2Ceil}

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

  out.stai := in.stai
  out.endi := in.endi

  val inputValid: Bool = Wire(Bool())
  val outputReady: Bool = Wire(Bool())
  private val inputLastVec = in.lastVec
  private val outputLastVec = out.lastVec

  private val subProcessors = for (i <- 0 until n) yield {
    val processor: Instance[SubProcessorSignalDef] = Instance(processorDef)
    //    val processor: SubProcessor = Module(new SubProcessor)
    processor.in.stai := 0.U  // Static signal
    processor.in.endi := 0.U  // Static signal
    processor.in.strb := in.strb(i)
    processor.in.valid := inputValid
    processor.in.last := inputLastVec(i)
    processor.in.data := in.data((elSize*i+1)-1, elSize*i)  // Set data
    processor.in.user := in.user
    processor.out.ready := outputReady
    processor
  }

  out.user := subProcessors(0).out.user
  out.last := outputLastVec.asUInt

  private val inputReadies = subProcessors.map(_.in.ready)
  in.ready := inputReadies.reduce(_&&_)  // Top input is ready when all the modules are ready
  inputValid := in.ready && in.valid  // Wait for all processors to be ready before we transmit the valid signal to them

  private val outputValids = subProcessors.map(_.out.valid)
  out.valid := outputValids.reduce(_ && _) // Top output is valid when all the modules are valid
  outputReady := out.ready && out.valid  // Wait for all processors to be valid before we transmit the ready signal to them

  // Top lane is valid when sub is ready
  out.strb := subProcessors.map(_.out.strb).reduce(Cat(_,_))
  // Re-concat all processor output data
  out.data := subProcessors.map(_.out.data).reduce(Cat(_, _))
}
