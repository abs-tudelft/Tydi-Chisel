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

  val L_ShipInstructIn = lineItemsInStream.el.L_ShipInstruct.toPhysical
  val L_ShipModeIn = lineItemsInStream.el.L_ShipMode.toPhysical
  val L_CommentIn = lineItemsInStream.el.L_Comment.toPhysical
  val L_CommitDateIn = lineItemsInStream.el.L_CommitDate.toPhysical
  val L_LineStatusIn = lineItemsInStream.el.L_LineStatus.toPhysical
  val L_ReceiptDateIn = lineItemsInStream.el.L_ReceiptDate.toPhysical
  val L_ReturnFlagIn = lineItemsInStream.el.L_ReturnFlag.toPhysical
  val L_ShipDateIn = lineItemsInStream.el.L_ShipDate.toPhysical

  val L_ShipInstructOut = lineItemsOutStream.el.L_ShipInstruct.toPhysical
  val L_ShipModeOut = lineItemsOutStream.el.L_ShipMode.toPhysical
  val L_CommentOut = lineItemsOutStream.el.L_Comment.toPhysical
  val L_CommitDateOut = lineItemsOutStream.el.L_CommitDate.toPhysical
  val L_LineStatusOut = lineItemsOutStream.el.L_LineStatus.toPhysical
  val L_ReceiptDateOut = lineItemsOutStream.el.L_ReceiptDate.toPhysical
  val L_ReturnFlagOut = lineItemsOutStream.el.L_ReturnFlag.toPhysical
  val L_ShipDateOut = lineItemsOutStream.el.L_ShipDate.toPhysical

  val P_ContainerIn = partsInStream.el.P_Container.toPhysical
  val P_BrandIn = partsInStream.el.P_Brand.toPhysical
  val P_MfgrIn = partsInStream.el.P_Mfgr.toPhysical
  val P_NameIn = partsInStream.el.P_Name.toPhysical
  val P_CommentIn = partsInStream.el.P_Comment.toPhysical
  val P_TypeIn = partsInStream.el.P_Type.toPhysical

  val P_ContainerOut = partsOutStream.el.P_Container.toPhysical
  val P_BrandOut = partsOutStream.el.P_Brand.toPhysical
  val P_MfgrOut = partsOutStream.el.P_Mfgr.toPhysical
  val P_NameOut = partsOutStream.el.P_Name.toPhysical
  val P_CommentOut = partsOutStream.el.P_Comment.toPhysical
  val P_TypeOut = partsOutStream.el.P_Type.toPhysical
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
  val partsInStream = Part_stream().flip
  /** IO of [[partsInStream]] with input direction. */
  val partsIn = partsInStream.toPhysical
  /** Stream of [[revenueOut]] with output direction. */
  val revenueOutStream = RevenueStream()
  /** IO of [[revenueOutStream]] with output direction. */
  val revenueOut = revenueOutStream.toPhysical


  val L_ShipInstructIn = lineItemsInStream.el.L_ShipInstruct.toPhysical
  val L_ShipModeIn = lineItemsInStream.el.L_ShipMode.toPhysical
  val L_CommentIn = lineItemsInStream.el.L_Comment.toPhysical
  val L_CommitDateIn = lineItemsInStream.el.L_CommitDate.toPhysical
  val L_LineStatusIn = lineItemsInStream.el.L_LineStatus.toPhysical
  val L_ReceiptDateIn = lineItemsInStream.el.L_ReceiptDate.toPhysical
  val L_ReturnFlagIn = lineItemsInStream.el.L_ReturnFlag.toPhysical
  val L_ShipDateIn = lineItemsInStream.el.L_ShipDate.toPhysical

  val P_ContainerIn = partsInStream.el.P_Container.toPhysical
  val P_BrandIn = partsInStream.el.P_Brand.toPhysical
  val P_MfgrIn = partsInStream.el.P_Mfgr.toPhysical
  val P_NameIn = partsInStream.el.P_Name.toPhysical
  val P_CommentIn = partsInStream.el.P_Comment.toPhysical
  val P_TypeIn = partsInStream.el.P_Type.toPhysical
}
