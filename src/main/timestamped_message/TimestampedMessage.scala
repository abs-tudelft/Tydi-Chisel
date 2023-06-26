package timestamped_message

import tydi_lib._
import chisel3._
import chisel3.internal.firrtl.Width
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}

//////  End lib, start user code  //////

class NestedBundle extends Union(2) {
  val a: UInt = UInt(8.W)
  val b: Bool = Bool()
}

object NestedBundleChoices {
  val a: UInt = 0.U(1.W)
  val b: UInt = 1.U(1.W)
}

class TimestampedMessageBundle extends Group {
  private val charWidth: Width = 8.W
  val time: UInt = UInt(64.W)
  val nested: NestedBundle = new NestedBundle
  /*// It seems anonymous classes don't work well
  val nested: Group = new Group {
    val a: UInt = UInt(8.W)
    val b: Bool = Bool()
  }*/
  val message = new PhysicalStreamDetailed(BitsEl(charWidth), n = 3, d = 1, c = 7)
}

class TimestampedMessageModuleOut extends TydiModule {
  private val timestampedMessageBundle = new TimestampedMessageBundle // Can also be inline

  // Create Tydi logical stream object
  val stream: PhysicalStreamDetailed[TimestampedMessageBundle, Null] = PhysicalStreamDetailed(timestampedMessageBundle, 1, c = 7)

  // Create and connect physical streams following standard with concatenated data bitvector
  val tydi_port_top: PhysicalStream = stream.toPhysical
  val tydi_port_child: PhysicalStream = stream.el.message.toPhysical

  // → Assign values to logical stream group elements directly
  stream.el.time := System.currentTimeMillis().U
  stream.el.nested.a := 5.U
  stream.el.nested.b := true.B
  stream.el.nested.tag := NestedBundleChoices.b  // Using object value to set Union tag value
  stream.el.message.data(0).value := 'H'.U
  stream.el.message.data(1).value := 'e'.U
  stream.el.message.data(2).value := 'l'.U

  // → Assign some values to the other Tydi signals
  //   We have 1 lane in this case

  // Top stream
  stream.valid := true.B
  stream.strb := 1.U
  stream.stai := 0.U
  stream.endi := 1.U
  stream.last := 0.U

  // Child stream
  stream.el.message.valid := true.B
  stream.el.message.strb := 1.U
  stream.el.message.stai := 0.U
  stream.el.message.endi := 1.U
  stream.el.message.last := 0.U
}

class TimestampedMessageModuleIn extends Module {
  val io1 = IO(Flipped(new PhysicalStream(new TimestampedMessageBundle, n=1, d=2, c=7, u=new Null())))
  val io2 = IO(Flipped(new PhysicalStream(BitsEl(8.W), n=3, d=2, c=7, u=new Null())))
  io1 :<= DontCare
  io1.ready := DontCare
  io2 :<= DontCare
  io2.ready := DontCare
}

class TopLevelModule extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(64.W))
    val out = Output(SInt(128.W))
  })

  val timestampedMessageOut = Module(new TimestampedMessageModuleOut())
  val timestampedMessageIn = Module(new TimestampedMessageModuleIn())

  // Bi-directional connection
  timestampedMessageIn.io1 :<>= timestampedMessageOut.tydi_port_top
  timestampedMessageIn.io2 :<>= timestampedMessageOut.tydi_port_child
  io.out := timestampedMessageOut.tydi_port_top.data.asSInt
}

object TimestampedMessage extends App {
  private val firOpts: Array[String] = Array("-disable-opt", "-O=debug", "-disable-all-randomization", "-strip-debug-info"/*, "-preserve-values=all"*/)
  println("Test123")

  println((new NestedBundle).createEnum)

  println(emitCHIRRTL(new TimestampedMessageModuleOut()))
  println(emitSystemVerilog(new TimestampedMessageModuleOut(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new TimestampedMessageModuleIn()))
  println(emitSystemVerilog(new TimestampedMessageModuleIn(), firtoolOpts = firOpts))

  println(emitCHIRRTL(new TopLevelModule()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new TopLevelModule(), firtoolOpts = firOpts))

  println("Done")
}
