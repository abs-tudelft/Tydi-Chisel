package rgb

import tydi_lib._
import chisel3._
import chisel3.experimental.hierarchy._
import chisel3.util.Cat
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

@instantiable
abstract class SubProcessorSignalDef extends TydiModule {
  // Declare streams
  @public val out: PhysicalStream
  @public val in: PhysicalStream
}

@instantiable
abstract class SubProcessorBase[T <: TydiEl](val e: T) extends SubProcessorSignalDef {
  // Declare streams
  val outStream: PhysicalStreamDetailed[T] = PhysicalStreamDetailed(e, n = 1, d = 0, c = 1, r = false)
  val inStream: PhysicalStreamDetailed[T] = PhysicalStreamDetailed(e, n = 1, d = 0, c = 1, r = true)
  val out: PhysicalStream = outStream.toPhysical
  val in: PhysicalStream = inStream.toPhysical

  // Connect streams
  out :<>= in

  // Set static signals
  outStream.strb := 1.U
  outStream.stai := 0.U
  outStream.endi := 0.U
  // stai and endi are 0-length
}

/**
 * A simple rgb pixel processor with a 1-lane low complexity input stream.
 */
@instantiable
class SubProcessor extends SubProcessorBase(new RgbBundle) {
  // Do some data processing
  outStream.el.r := inStream.el.r * 2.U
  outStream.el.g := inStream.el.g * 2.U
  outStream.el.b := inStream.el.b * 2.U
  outStream.last := DontCare
  outStream.valid := true.B // Fixme
  inStream.ready := true.B
}


/**
 * A MIMO pixel processor that consists of multiple sub-processors.
 */
class MainProcessor extends MultiProcessorGeneral(new RgbBundle, Definition(new SubProcessor), 6)

/**
 * A MIMO pixel processor that consists of multiple sub-processors.
 * @param n Number of lanes/sub-processors
 */
class MultiProcessorGeneral(val e: TydiEl, val processorDef: Definition[SubProcessorSignalDef], val n: Int = 6) extends TydiModule {
  val out: PhysicalStream = IO(new PhysicalStream(e, n=n, d=0, c=7))
  val in: PhysicalStream = IO(Flipped(new PhysicalStream(e, n=n, d=0, c=7)))

  val elSize: Int = e.getWidth

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
    processor.out.ready := out.ready
    processor
  }

  private val inputReadies = subProcessors.map(_.in.ready)
  in.ready := inputReadies.reduce(_&&_)  // Top input is ready when all the modules are ready

  // Top lane is valid when sub is ready Todo: factor in out ready here
  out.strb := subProcessors.map(_.out.strb).reduce(Cat(_,_))
  // Re-concat all processor output data
  out.data := subProcessors.map(_.out.data).reduce(Cat(_, _))
}


object RgbMultiProcessing extends App {
  println(emitCHIRRTL(new MainProcessor()))

  private val noOptimizationVerilog: String = emitSystemVerilog(new MainProcessor(), firtoolOpts = firNoOptimizationOpts)
  private val normalVerilog: String = emitSystemVerilog(new MainProcessor(), firtoolOpts = firNormalOpts)
//  private val optimizedVerilog: String = emitSystemVerilog(new MainProcessor(), firtoolOpts = firReleaseOpts)
  //  println("\n\n\n")
  //  println(noOptimizationVerilog)
  println("\n\n\n")
  println(normalVerilog)
  println("\n\n\n")
//  println(optimizedVerilog)
//  println("\n\n\n")
  println(s"No optimization: ${noOptimizationVerilog.lines.count()} lines")
  println(s"Debug optimization: ${normalVerilog.lines.count()} lines")
//  println(s"Release optimization: ${optimizedVerilog.lines.count()} lines")
}
