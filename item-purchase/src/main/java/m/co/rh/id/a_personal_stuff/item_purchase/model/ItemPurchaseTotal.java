package m.co.rh.id.a_personal_stuff.item_purchase.model;

import androidx.room.ColumnInfo;

public class ItemPurchaseTotal {
    @ColumnInfo(name = "itemId")
    public long itemId;

    @ColumnInfo(name = "total")
    public int total;
}
