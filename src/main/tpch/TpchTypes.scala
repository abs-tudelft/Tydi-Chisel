package tpch

import tydi_lib._
import chisel3._

object MyTypes {
  /** Bit(8) type, defined in pack0 */
  def char: UInt = UInt(8.W)
  assert(this.char.getWidth == 8)

  /** Bit(32) type, defined in pack0 */
  def integer: SInt = SInt(32.W)
  assert(this.integer.getWidth == 32)

  /** Bit(64) type, defined in pack0 */
  def real: UInt = Bits(64.W)
  assert(this.real.getWidth == 64)
}

class OptionalText extends Union(2) {
  val text = TextStream()
  val null_val: Null = Null()
}

/** Stream, defined in pack0. */
class TextStream(n: Int = 1) extends PhysicalStreamDetailed(e=MyTypes.char, n=n, d=1, c=1, r=false, u=Null())

object TextStream {
  def apply(n: Int = 1): TextStream = new TextStream(n)
}

/** Stream, defined in pack0. */
class OptionalTextStream(n: Int = 1) extends PhysicalStreamDetailed(e=new OptionalText, n=n, d=1, c=1, r=false, u=Null())

object OptionalTextStream {
  def apply(n: Int = 1): TextStream = new TextStream(n)
}



/** class element extends Group, defined in pack0. */
class Part extends Group {
  val P_Brand = TextStream()
  val P_Comment = TextStream()
  val P_Container = TextStream()
  val P_Mfgr = TextStream()
  val P_Name = TextStream()
  val P_PartKey = MyTypes.integer
  val P_RetailPrice = MyTypes.real
  val P_Size = MyTypes.integer
  val P_Type = TextStream()
}
class Part_stream extends PhysicalStreamDetailed(new Part, n=1, d=1, c=1)
object Part_stream {
  def apply(): Part_stream = new Part_stream()
}

// NATION TABLE
class Nation extends Group {
  val N_NationKey = MyTypes.integer
  val N_Name = TextStream()
  val N_RegionKey = MyTypes.integer // FOREIGN KEY REFERENCES Region
  val N_Comment = OptionalTextStream()
}
class Nation_stream extends PhysicalStreamDetailed(new Nation, n=1, d=1, c=1)
object Nation_stream {
  def apply(): Nation_stream = new Nation_stream()
}

// SUPPLIER TABLE
class Supplier extends Group {
  val S_SuppKey = MyTypes.integer
  val S_Name = TextStream()
  val S_Address = TextStream()
  val S_NationKey = MyTypes.integer // FOREIGN KEY REFERENCES Nation
  val S_Phone = TextStream()
  val S_AcctBal = MyTypes.real
  val S_Comment = TextStream()
}
class Supplier_stream extends PhysicalStreamDetailed(new Supplier, n=1, d=1, c=1)
object Supplier_stream {
  def apply(): Supplier_stream = new Supplier_stream()
}

// PARTSUPP TABLE
class Partsupp extends Group {
  val PS_PartKey = MyTypes.integer // FOREIGN KEY REFERENCES Part
  val PS_SuppKey = MyTypes.integer // FOREIGN KEY REFERENCES Supplier
  val PS_AvailQty = MyTypes.integer
  val PS_SupplyCost = MyTypes.real
  val PS_Comment = TextStream()
}
class Partsupp_stream extends PhysicalStreamDetailed(new Partsupp, n=1, d=1, c=1)
object Partsupp_stream {
  def apply(): Partsupp_stream = new Partsupp_stream()
}

// CUSTOMER TABLE
class Customer extends Group {
  val C_CustKey = MyTypes.integer
  val C_Name = TextStream()
  val C_Address = TextStream()
  val C_NationKey = MyTypes.integer // FOREIGN KEY REFERENCES Nation
  val C_Phone = TextStream()
  val C_AcctBal = MyTypes.real
  val C_MktSegment = TextStream()
  val C_Comment = TextStream()
}
class Customer_stream extends PhysicalStreamDetailed(new Customer, n=1, d=1, c=1)
object Customer_stream {
  def apply(): Customer_stream = new Customer_stream()
}

// ORDERS TABLE
class Orders extends Group {
  val O_OrderKey = MyTypes.integer
  val O_CustKey = MyTypes.integer // FOREIGN KEY REFERENCES Customer
  val O_OrderStatus = TextStream()
  val O_TotalPrice = MyTypes.real
  val O_OrderDate = TextStream()
  val O_OrderPriority = TextStream()
  val O_Clerk = TextStream()
  val O_ShipPriority = MyTypes.integer
  val O_Comment = TextStream()
}
class Orders_stream extends PhysicalStreamDetailed(new Orders, n=1, d=1, c=1)
object Orders_stream {
  def apply(): Orders_stream = new Orders_stream()
}

/** class element extends Group, defined in pack0. */
class LineItem extends Group {
  val L_Comment = TextStream()
  val L_CommitDate = TextStream()
  val L_Discount = MyTypes.real
  val L_ExtendedPrice = MyTypes.real
  val L_LineNumber = MyTypes.integer
  val L_LineStatus = TextStream()
  val L_OrderKey = MyTypes.integer
  val L_PartKey = MyTypes.integer
  val L_Quantity = MyTypes.integer
  val L_ReceiptDate = TextStream()
  val L_ReturnFlag = TextStream()
  val L_ShipDate = TextStream()
  val L_ShipInstruct = TextStream()
  val L_ShipMode = TextStream()
  val L_SuppKey = MyTypes.integer
  val L_Tax = MyTypes.real
}
class LineItem_stream extends PhysicalStreamDetailed(new LineItem, n=1, d=1, c=1)
object LineItem_stream {
  def apply(): LineItem_stream = new LineItem_stream()
}
