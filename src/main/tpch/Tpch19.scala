package tpch
import chisel3._
import tydi_lib._
import tydi_lib.utils.StringComparator

/**
 * Implementation, defined in pack0.
 */
class Tphc19_Filter extends Tphc19_Filter_interface {}

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
