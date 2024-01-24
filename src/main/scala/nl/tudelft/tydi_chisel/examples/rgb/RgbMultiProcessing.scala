package nl.tudelft.tydi_chisel.examples.rgb

import nl.tudelft.tydi_chisel._
import chisel3._
import chisel3.experimental.hierarchy._
import chisel3.util.Cat
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

/**
 * A simple rgb pixel processor with a 1-lane low complexity input stream.
 */
class SubProcessor extends SubProcessorBase(new RgbBundle, new RgbBundle) {
  // Do some data processing
  outStream.el.r  := inStream.el.r * 2.U
  outStream.el.g  := inStream.el.g * 2.U
  outStream.el.b  := inStream.el.b * 2.U
  outStream.last  := DontCare
  outStream.valid := true.B // Fixme
  inStream.ready  := true.B
}

/**
 * A MIMO pixel processor that consists of multiple sub-processors.
 */
class MainProcessor extends MultiProcessorGeneral(Definition(new SubProcessor), 6, new RgbBundle, new RgbBundle)

object RgbMultiProcessing extends App {
  import chiseltest.RawTester.test

  test(new MainProcessor()) { c =>
    val str = c.tydiCode
    println(str)
  }
  println(emitCHIRRTL(new MainProcessor()))

  private val noOptimizationVerilog: String =
    emitSystemVerilog(new MainProcessor(), firtoolOpts = firNoOptimizationOpts)
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
