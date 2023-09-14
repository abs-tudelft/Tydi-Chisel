package tpch
import chisel3._
import tpch._
import tydi_lib._

class Revenue extends Group {
  val value: UInt = MyTypes.real
}

/** Stream, defined in pack0. */
class RevenueStream extends PhysicalStreamDetailed(e=new Revenue, n=1, d=1, c=1, r=false, u=Null())

object RevenueStream {
  def apply(): RevenueStream = Wire(new RevenueStream())
}

/**
 * Streamlet, defined in pack0.
 */
class Tphc19_Filter_interface extends TydiModule {
  /** Stream of [[lineItemsIn]] with input direction. */
  val lineItemsInStream = LineItem_stream().flip
  /** IO of [[lineItemsInStream]] with input direction. */
  val lineItemsIn = lineItemsInStream.toPhysical
  /** Stream of [[lineItemsOut]] with output direction. */
  val lineItemsOutStream = LineItem_stream()
  /** IO of [[lineItemsOutStream]] with output direction. */
  val lineItemsOut = lineItemsOutStream.toPhysical
  /** Stream of [[partsIn]] with input direction. */
  val partsInStream = Part_stream().flip
  /** IO of [[partsInStream]] with input direction. */
  val partsIn = partsInStream.toPhysical
  /** Stream of [[partsOut]] with output direction. */
  val partsOutStream = Part_stream()
  /** IO of [[partsOutStream]] with output direction. */
  val partsOut = partsOutStream.toPhysical
}

/**
 * Streamlet, defined in pack0.
 */
class Tphc19_Top_interface extends TydiModule {
  /** Stream of [[lineItemsIn]] with input direction. */
  val lineItemsInStream = LineItem_stream().flip
  /** IO of [[lineItemsInStream]] with input direction. */
  val lineItemsIn = lineItemsInStream.toPhysical
  /** Stream of [[partsIn]] with input direction. */
  val partsInStream = LineItem_stream().flip
  /** IO of [[partsInStream]] with input direction. */
  val partsIn = partsInStream.toPhysical
  /** Stream of [[revenueOut]] with output direction. */
  val revenueOutStream = RevenueStream()
  /** IO of [[revenueOutStream]] with output direction. */
  val revenueOut = revenueOutStream.toPhysical
}
