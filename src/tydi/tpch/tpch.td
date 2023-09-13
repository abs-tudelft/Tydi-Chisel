package pack0;

// Character type
Char = Bit(8);
// Integer type
Integer = Bit(4*8);
// Double type
Real = Bit(8*8);

// Stream for non-nullable text
TextStream = Stream(Char, t=1.0, d=1);

# This Union specifies a nullable text #
Union OptionalText {
    text: TextStream;
    null: Null;
}

// Stream for nullable text
OptionalTextStream = Stream(OptionalText, t=1.0, d=0);

Group Region {
    R_RegionKey: Integer;
    R_Name: TextStream;
    R_Comment: OptionalTextStream;
}

Region_stream = Stream(Region, t=1.0, d=1);

// NATION TABLE
Group Nation {
    N_NationKey: Integer;
    N_Name: TextStream;
    N_RegionKey: Integer; // FOREIGN KEY REFERENCES Region
    N_Comment: OptionalTextStream;
}
Nation_stream = Stream(Nation, t=1.0, d=1);

// PART TABLE
Group Part {
    P_PartKey: Integer;
    P_Name: TextStream;
    P_Mfgr: TextStream;
    P_Brand: TextStream;
    P_Type: TextStream;
    P_Size: Integer;
    P_Container: TextStream;
    P_RetailPrice: Real;
    P_Comment: TextStream;
}
Part_stream = Stream(Part, t=1.0, d=1);

// SUPPLIER TABLE
Group Supplier {
    S_SuppKey: Integer;
    S_Name: TextStream;
    S_Address: TextStream;
    S_NationKey: Integer; // FOREIGN KEY REFERENCES Nation
    S_Phone: TextStream;
    S_AcctBal: Real;
    S_Comment: TextStream;
}
Supplier_stream = Stream(Supplier, t=1.0, d=1);

// PARTSUPP TABLE
Group Partsupp {
    PS_PartKey: Integer; // FOREIGN KEY REFERENCES Part
    PS_SuppKey: Integer; // FOREIGN KEY REFERENCES Supplier
    PS_AvailQty: Integer;
    PS_SupplyCost: Real;
    PS_Comment: TextStream;
}
Partsupp_stream = Stream(Partsupp, t=1.0, d=1);

// CUSTOMER TABLE
Group Customer {
    C_CustKey: Integer;
    C_Name: TextStream;
    C_Address: TextStream;
    C_NationKey: Integer; // FOREIGN KEY REFERENCES Nation
    C_Phone: TextStream;
    C_AcctBal: Real;
    C_MktSegment: TextStream;
    C_Comment: TextStream;
}
Customer_stream = Stream(Customer, t=1.0, d=1);

// ORDERS TABLE
Group Orders {
    O_OrderKey: Integer;
    O_CustKey: Integer; // FOREIGN KEY REFERENCES Customer
    O_OrderStatus: TextStream;
    O_TotalPrice: Real;
    O_OrderDate: TextStream;
    O_OrderPriority: TextStream;
    O_Clerk: TextStream;
    O_ShipPriority: Integer;
    O_Comment: TextStream;
}
Orders_stream = Stream(Orders, t=1.0, d=1);

// LINEITEM TABLE
Group LineItem {
    L_OrderKey: Integer; // FOREIGN KEY REFERENCES Orders
    L_PartKey: Integer; // FOREIGN KEY REFERENCES Partsupp
    L_SuppKey: Integer; // FOREIGN KEY REFERENCES Partsupp
    L_LineNumber: Integer;
    L_Quantity: Integer;
    L_ExtendedPrice: Real;
    L_Discount: Real;
    L_Tax: Real;
    L_ReturnFlag: TextStream;
    L_LineStatus: TextStream;
    L_ShipDate: TextStream;
    L_CommitDate: TextStream;
    L_ReceiptDate: TextStream;
    L_ShipInstruct: TextStream;
    L_ShipMode: TextStream;
    L_Comment: TextStream;
}
LineItem_stream = Stream(LineItem, t=1.0, d=1);

Revenue_stream = Stream(Real, t=1.0, d=1);

streamlet Tphc19_Top_interface {
    lineItemsIn: LineItem_stream in;
    partsIn: Part_stream in;
    revenueOut: Revenue_stream out;
}

streamlet Tphc19_Filter_interface {
    lineItemsIn: LineItem_stream in;
    partsIn: Part_stream in;
    lineItemsOut: LineItem_stream in;
    partsOut: Part_stream in;
}

impl Tphc19_Filter of Tphc19_Filter_interface {}
impl Tphc19_Reducer of Tphc19_Top_interface {}

impl Tphc19_Top of Tphc19_Top_interface {
    instance filter(Tphc19_Filter);
    instance reducer(Tphc19_Reducer);

    self.lineItemsIn => filter.lineItemsIn;
    self.partsIn => filter.partsIn;
    filter.lineItemsIn => reducer.lineItemsIn;
    filter.partsIn => reducer.partsIn;
    reducer.revenueOut => self.revenueOut;
}
