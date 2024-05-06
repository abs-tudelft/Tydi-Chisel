package nl.tudelft.tydi_chisel

import chiseltest._
import nl.tudelft.tydi_chisel.examples.pipeline.{PipelineExampleModule, PipelinePlusModule}
import nl.tudelft.tydi_chisel.examples.rgb.MainProcessor
import nl.tudelft.tydi_chisel.examples.timestamped_message.TopLevelModule
import org.scalatest.flatspec.AnyFlatSpec

class ReverseTranspileTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "reverse transpilation"

  it should "reverse transpile" in {
    // Test reverse transpilation of Chisel to Tydi-Lang code for various modules.
    test(new PipelineExampleModule()) { c => println(c.tydiCode) }
    test(new PipelinePlusModule()) { c => println(c.tydiCode) }
    test(new MainProcessor()) { c => println(c.tydiCode) }
    test(new TopLevelModule()) { c => println(c.tydiCode) }
  }
}
