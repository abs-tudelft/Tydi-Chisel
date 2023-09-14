package tpch
import chisel3._
import chiseltest.RawTester.test
import circt.stage.ChiselStage.{emitCHIRRTL, emitSystemVerilog}
import tydi_lib._
import tydi_lib.utils.StringComparator

class FilterCheck extends Bundle {
  val check: Bool = Bool()
  val ready: Bool = Bool()
}

object FilterCheck {
  def apply(check: Bool, ready: Bool): FilterCheck = {
    val bundle = Wire(new FilterCheck())
    bundle.check := check
    bundle.ready := ready
    bundle
  }
}

/**
 * Implementation, defined in pack0.
 */
class Tphc19_Filter extends Tphc19_Filter_interface {
  // Check p_brand = 'Brand#22'
  private val brandCheckMod = Module(new StringComparator("Brand#22"))
  brandCheckMod.in := partsInStream.el.P_Brand
  val brandCheck: FilterCheck = FilterCheck(brandCheckMod.out.data(0), brandCheckMod.out.valid)

  // Check p_container in ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG')
  private val containerTypes: Seq[String] = Seq("SM CASE", "SM BOX", "SM PACK", "SM PKG")
  private val containerCheckMods: Seq[StringComparator] = containerTypes.map(new StringComparator(_)).map(Module(_))
  for (elem <- containerCheckMods) {
    elem.in := partsInStream.el.P_Container
  }
  val containerCheck: FilterCheck = FilterCheck(
    containerCheckMods.map(_.out.data(0)).reduce(_ || _),
    containerCheckMods.map(_.out.valid).reduce(_ && _)
  )

  // Check l_shipmode in ('AIR', 'AIR REG')
  private val shipModeTypes: Seq[String] = Seq("AIR", "AIR REG")
  private val shipModeCheckMods: Seq[StringComparator] = shipModeTypes.map(new StringComparator(_)).map(Module(_))
  for (elem <- shipModeCheckMods) {
    elem.in := lineItemsInStream.el.L_ShipMode
  }
  val shipModeCheck: FilterCheck = FilterCheck(
    shipModeCheckMods.map(_.out.data(0)).reduce(_ || _),
    shipModeCheckMods.map(_.out.valid).reduce(_ && _)
  )

  // Check l_shipinstruct = 'DELIVER IN PERSON'
  private val shipInstructMod = Module(new StringComparator("DELIVER IN PERSON"))
  shipInstructMod.in := lineItemsInStream.el.L_ShipInstruct
  val shipInstructCheck: FilterCheck = FilterCheck(shipInstructMod.out.data(0), shipInstructMod.out.valid)

  // Check l_quantity >= 8 and l_quantity <= 8 + 10
  val quanityCheck: Bool = lineItemsInStream.el.L_Quantity >= 8.S && lineItemsInStream.el.L_Quantity <= (8+10).S
  // Check p_size between 1 and 5
  val pSizeCheck: Bool = partsInStream.el.P_Size > 1.S && partsInStream.el.P_Size < 5.S

  // When all checks are ready, do the transfers
  when (brandCheck.ready && containerCheck.ready && shipModeCheck.ready && shipInstructCheck.ready) {
    brandCheckMod.out.ready := true.B
    for (elem <- containerCheckMods) {
      elem.out.ready := true.B
    }
    for (elem <- shipModeCheckMods) {
      elem.out.ready := true.B
    }
    shipInstructMod.out.ready := true.B

    lineItemsInStream.ready := true.B
    partsInStream.ready := true.B
  }

  val condition1: Bool = brandCheck.check && containerCheck.check && quanityCheck && pSizeCheck && shipModeCheck.check && shipInstructCheck.check
  val condition2: Bool = true.B
  val condition3: Bool = true.B

  val includeItem: Bool = condition1 || condition2 || condition3

  lineItemsOutStream.strb := includeItem
  partsInStream.strb := includeItem
}

/**
 * Implementation, defined in pack0.
 */
class Tphc19_Reducer extends Tphc19_Top_interface {}

/**
 * Implementation, defined in pack0.
 */
class Tphc19_Top extends Tphc19_Top_interface {
  // Modules
  val filter = Module(new Tphc19_Filter)
  val reducer = Module(new Tphc19_Reducer)

  // Connections
  filter.lineItemsIn := lineItemsIn
  filter.partsIn := partsIn
  reducer.lineItemsIn := filter.lineItemsIn
  reducer.partsIn := filter.partsIn
  revenueOut := reducer.revenueOut
}

object Tpch19 extends App {
  println("Test123")

  test(new Tphc19_Top()) { c =>
    println(c.tydiCode)
  }

  //  println(emitCHIRRTL(new Tphc19_Filter()))
  //  println(emitSystemVerilog(new Tphc19_Filter(), firtoolOpts = firNormalOpts))

  //  println(emitCHIRRTL(new Tphc19_Reducer()))
  //  println(emitSystemVerilog(new Tphc19_Reducer(), firtoolOpts = firNormalOpts))

  println(emitCHIRRTL(new Tphc19_Top()))

  // These lines generate the Verilog output
  println(emitSystemVerilog(new Tphc19_Top(), firtoolOpts = firNormalOpts))

  println("Done")
}
