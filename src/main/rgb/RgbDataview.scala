package main.rgb

import chisel3._
import chisel3.util.{Cat, Decoupled, log2Ceil}
import chisel3.experimental.dataview._
import chisel3.internal.firrtl.Width
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

/*class RgbBundle extends Group {
  private val channelWidth: Width = 8.W
  val r: UInt = UInt(channelWidth)
  val g: UInt = UInt(channelWidth)
  val b: UInt = UInt(channelWidth)
}*/

class RgbRaw extends Group {
  val data: UInt = UInt(24.W)
}

object RgbRaw {
  // Don't be afraid of the use of implicits, we will discuss this pattern in more detail later
  implicit val axiView = DataView[RgbRaw, RgbBundle](
    // The first argument is a function constructing an object of View type (RgbBundle)
    // from an object of the Target type (RgbRaw)
    vab => new RgbBundle(),
    // The remaining arguments are a mapping of the corresponding fields of the two types
    (raw, fancy) => raw.data -> fancy.r ## fancy.g ## fancy.b
  )
}

class RgbDataviewOut extends Module {
  private val rgbBundle = new RgbBundle // Can also be inline
  // Create and connect data part of physical stream
  val tydi_port: RgbRaw = IO(new RgbRaw)

  val view = tydi_port.viewAs[RgbBundle]

  // Assign values to logical stream group elements directly
  private val rgbVal:Seq[Int] = Seq(0, 166, 244)  // TU Delft blue
  view.r := rgbVal(0).U
  view.g := rgbVal(1).U
  view.b := rgbVal(2).U
}

class RgbDataviewIn extends Module {
  val io = IO(Flipped(new PhysicalStream(new RgbBundle, n=1, d=2, c=7, u=new Null())))
  io :<= DontCare
  io.ready := DontCare
}

class TopLevelRgbDataviewModule extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(64.W))
    val out = Output(SInt(128.W))
  })

  val rgbOut = Module(new RgbDataviewOut())
  val rgbIn = Module(new RgbDataviewIn())

  // Bi-directional connection
  rgbIn.io.data :<>= rgbOut.tydi_port.data
  io.out := rgbOut.tydi_port.data.asSInt
}

object RgbDataview extends App {
  private val firOpts: Array[String] = Array("-disable-opt", "-O=debug", "-disable-all-randomization", "-strip-debug-info"/*, "-preserve-values=all"*/)
  println("Test123")

  println(emitCHIRRTL(new RgbDataviewOut()))
  println(emitSystemVerilog(new RgbDataviewOut(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new RgbDataviewIn()))
  println(emitSystemVerilog(new RgbDataviewIn(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new TopLevelModule()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new TopLevelRgbDataviewModule(), firtoolOpts = firOpts))

  println("Done")
}
