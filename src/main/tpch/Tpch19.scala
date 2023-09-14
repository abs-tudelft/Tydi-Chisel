package tpch
import chisel3._
import tydi_lib._
import tydi_lib.utils.StringComparator

/**
 * Implementation, defined in pack0.
 */
class Tphc19_Filter extends Tphc19_Filter_interface {
  private val brandCheckMod = Module(new StringComparator("Brand#22"))
  brandCheckMod.in := partsInStream.el.P_Brand
  val brandCheck: Bool = brandCheckMod.out.data(0)

  private val containerTypes: Seq[String] = Seq("SM CASE", "SM BOX", "SM PACK", "SM PKG")
  private val containerCheckMods: Seq[StringComparator] = containerTypes.map(Module(new StringComparator(_)))
  for (elem <- containerCheckMods) {
    elem.in := partsInStream.el.P_Container
  }
  val containerCheck: Bool = containerCheckMods.map(_.out.data(0)).reduce(_ || _)

  private val shipModeTypes: Seq[String] = Seq("AIR", "AIR REG")
  private val shipModeCheckMods: Seq[StringComparator] = shipModeTypes.map(Module(new StringComparator(_)))
  for (elem <- shipModeCheckMods) {
    elem.in := lineItemsInStream.el.L_ShipMode
  }
  val shipModeCheck: Bool = shipModeCheckMods.map(_.out.data(0)).reduce(_ || _)

  private val shipInstructMod = Module(new StringComparator("DELIVER IN PERSON"))
  shipInstructMod.in := lineItemsInStream.el.L_ShipInstruct
  val shipInstructCheck: Bool = shipInstructMod.out.data(0)

  val quanityCheck: Bool = lineItemsInStream.el.L_Quantity >= 8.S && lineItemsInStream.el.L_Quantity <= (8+10).S
  val pSizeCheck: Bool = partsInStream.el.P_Size > 1.S && partsInStream.el.P_Size < 5.S

  val condition1: Bool = brandCheck && containerCheck && quanityCheck && pSizeCheck && shipModeCheck && shipInstructCheck
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
