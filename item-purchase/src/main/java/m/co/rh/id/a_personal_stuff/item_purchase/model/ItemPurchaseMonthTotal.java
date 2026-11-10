package m.co.rh.id.a_personal_stuff.item_purchase.model;

import androidx.room.ColumnInfo;

public class ItemPurchaseMonthTotal {
    @ColumnInfo(name = "month")
    public String month;

    @ColumnInfo(name = "total")
    public Double total;
}
