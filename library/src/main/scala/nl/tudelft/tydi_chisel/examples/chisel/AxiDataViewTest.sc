import chisel3._
import chisel3.experimental.dataview._
import chisel3.util.Decoupled
import circt.stage.ChiselStage.emitCHIRRTL

// Note that both the AW and AR channels look similar and could use the same Bundle definition
class AXIAddressChannel(val addrWidth: Int) extends Bundle {
  val id = UInt(4.W)
  val addr = UInt(addrWidth.W)
  val len = UInt(2.W)
  val size = UInt(2.W)
  // ...
}

class VerilogAXIBundle(val addrWidth: Int) extends Bundle {
  val AWVALID = Output(Bool()) // Write valid signal for write transactions
  val AWREADY = Input(Bool()) // Write ready signal for write transactions
  val AWID = Output(UInt(4.W)) // Write ID signal for write transactions
  val AWADDR = Output(UInt(addrWidth.W)) // Address bus for write transactions
  val AWLEN = Output(UInt(2.W)) // Transaction length for burst
  val AWSIZE = Output(UInt(2.W)) // Maximum number of bytes to transfer in each data transfer

  val ARVALID = Output(Bool()) // Read valid signal for read transactions
  val ARREADY = Input(Bool()) // Read ready signal for read transactions
  val ARID = Output(UInt(4.W)) // Read ID signal for read transactions
  val ARADDR = Output(UInt(addrWidth.W)) // Address bus for read transactions
  val ARLEN = Output(UInt(2.W)) // Transaction length for burst
  val ARSIZE = Output(UInt(2.W)) // Maximum number of bytes to transfer in each data transfer
//  val AWBURST = Output(UInt(2.W)) // Burst type of the transaction
//  val AWCACHE = Output(UInt(4.W)) // Caching policy
//  val AWPROT = Output(UInt(3.W)) // Protection level
  // The rest of AW, AR, and other AXI channels here

  /*
  * The AXI manual says components must support all combinations of inputs, but do not have
  * to generate all combinations of outputs. If an output value is default, it can be omitted.
  * An input signal can be omitted if the master or slave does not need to observe the input
  * signal for correct functional operation.
  * */
}

// Instantiated as
class my_module extends RawModule {
  val AXI = IO(new VerilogAXIBundle(20))
}

println(emitCHIRRTL(new my_module))

class AXIBundle(val addrWidth: Int) extends Bundle {
  val aw = Decoupled(new AXIAddressChannel(addrWidth))
  val ar = Decoupled(new AXIAddressChannel(addrWidth))
  // ... Other channels here ...
}

// We recommend putting DataViews in a companion object of one of the involved types
object AXIBundle {
  // Don't be afraid of the use of implicits, we will discuss this pattern in more detail later
  implicit val axiView = DataView[VerilogAXIBundle, AXIBundle](
    // The first argument is a function constructing an object of View type (AXIBundle)
    // from an object of the Target type (VerilogAXIBundle)
    vab => new AXIBundle(vab.addrWidth),
    // The remaining arguments are a mapping of the corresponding fields of the two types
    _.AWVALID -> _.aw.valid,
    _.AWREADY -> _.aw.ready,
    _.AWID -> _.aw.bits.id,
    _.AWADDR -> _.aw.bits.addr,
    _.AWLEN -> _.aw.bits.len,
    _.AWSIZE -> _.aw.bits.size,

    _.ARVALID -> _.ar.valid,
    _.ARREADY -> _.ar.ready,
    _.ARID -> _.ar.bits.id,
    _.ARADDR -> _.ar.bits.addr,
    _.ARLEN -> _.ar.bits.len,
    _.ARSIZE -> _.ar.bits.size,
    // ...
  )
}

class AXIStub extends RawModule {
  val AXI = IO(new VerilogAXIBundle(20))
  val view = AXI.viewAs[AXIBundle]

  // We can now manipulate `AXI` via `view`
  view.aw.bits := 0.U.asTypeOf(new AXIAddressChannel(20)) // zero everything out by default
  view.aw.valid := true.B
  when (view.aw.ready) {
    view.aw.bits.id := 5.U
    view.aw.bits.addr := 1234.U
    // We can still manipulate AXI as well
    AXI.AWLEN := 1.U
  }
}

println(emitCHIRRTL(new AXIStub))
