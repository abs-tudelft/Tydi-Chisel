package nl.tudelft.tydi_chisel.examples.rgb

import nl.tudelft.tydi_chisel._
import chisel3._
import chisel3.internal.firrtl.Width
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

class RgbBundle extends Group {
  private val channelWidth: Width = 8.W
  val r: UInt                     = UInt(channelWidth)
  val g: UInt                     = UInt(channelWidth)
  val b: UInt                     = UInt(channelWidth)
}

class RgbModuleOut extends Module {
  private val rgbBundle = new RgbBundle // Can also be inline
  // Create Tydi logical stream object
  val stream: PhysicalStreamDetailed[RgbBundle, Null] = PhysicalStreamDetailed(rgbBundle, 1, c = 7)
  // Create and connect physical stream following standard with concatenated data bitvector
  val tydi_port: PhysicalStream = stream.toPhysical

  // Assign values to logical stream group elements directly
  private val rgbVal: Seq[Int] = Seq(0, 166, 244) // TU Delft blue
  stream.el.r := rgbVal(0).U
  stream.el.g := rgbVal(1).U
  stream.el.b := rgbVal(2).U

  // Assign some values to the other Tydi signals
  // We have 1 lane in this case
  stream.valid := true.B
  stream.strb  := 1.U
  stream.stai  := 0.U
  stream.endi  := 1.U
  stream.last  := 0.U
}

class RgbModuleIn extends Module {
  val io = IO(Flipped(new PhysicalStream(new RgbBundle, n = 1, d = 2, c = 7, u = new Null())))
  io :<= DontCare
  io.ready := DontCare
}

class TopLevelModule extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(64.W))
    val out = Output(SInt(128.W))
  })

  val rgbOut = Module(new RgbModuleOut())
  val rgbIn  = Module(new RgbModuleIn())

  // Bi-directional connection
  rgbIn.io := rgbOut.tydi_port
  io.out   := rgbOut.tydi_port.data.asSInt
}

object Rgb extends App {
  private val firOpts: Array[String] =
    Array("-disable-opt", "-O=debug", "-disable-all-randomization", "-strip-debug-info" /*, "-preserve-values=all"*/ )
  println("Test123")

  println(emitCHIRRTL(new RgbModuleOut()))
  println(emitSystemVerilog(new RgbModuleOut(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new RgbModuleIn()))
  println(emitSystemVerilog(new RgbModuleIn(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new TopLevelModule()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new TopLevelModule(), firtoolOpts = firOpts))

  println("Done")
}
