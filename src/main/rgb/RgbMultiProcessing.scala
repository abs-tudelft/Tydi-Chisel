package rgb

import tydi_lib._
import chisel3._
import chisel3.util.{Cat}
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

/**
 * A simple rgb pixel processor with a 1-lane low complexity input stream.
 */
class SubProcessor extends TydiModule {
  // Declare streams
  private val outStream = PhysicalStreamDetailed(new RgbBundle, n = 1, d = 0, c = 1, r = false)
  private val inStream = PhysicalStreamDetailed(new RgbBundle, n = 1, d = 0, c = 1, r = true)
  val out: PhysicalStream = outStream.toPhysical
  val in: PhysicalStream = inStream.toPhysical

  // Do some data processing
  outStream.el.r := outStream.el.r * 2.U
  outStream.el.g := outStream.el.g * 2.U
  outStream.el.b := outStream.el.b * 2.U

  // Connect streams
  out :<>= in

  // Set static signals
  outStream.strb := 1.U
  // stai and endi are 0-length
}


/**
 * A MIMO pixel processor that consists of multiple sub-processors.
 * @param n Number of lanes/sub-processors
 */
class MainProcessor(val n: Int = 6) extends TydiModule {
  private val inStream = PhysicalStreamDetailed(new RgbBundle, n=n, d=0, c=7, r=false)
  private val outStream = inStream.flip
  val in: PhysicalStream = inStream.toPhysical
  val out: PhysicalStream = outStream.toPhysical

  val elSize: Int = (new RgbBundle).elWidth

  private val subProcessors = for (i <- 0 until n) yield {
    val processor = Module(new SubProcessor)
    // Set static signals
    processor.in.strb := 1.U
//    outStream.strb(i) := processor.out.ready // Top lane is valid when sub is ready Todo: factor in out ready here
    processor.in.valid := inStream.valid(i)  // Sub input is valid when lane is valid
    processor.in.data := in.data((elSize*i+1)-1, elSize*i)  // Set data
    processor
  }

  private val inputReadies = subProcessors.map(_.in.ready)
  inStream.ready := inputReadies.reduce(_&&_)  // Top input is ready when all the modules are ready

  // Top lane is valid when sub is ready Todo: factor in out ready here
  outStream.strb := subProcessors.map(_.out.strb).reduce(Cat(_,_))
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
