package m.co.rh.id.a_personal_stuff.item_usage.model;

import androidx.room.ColumnInfo;

public class ItemUsageTotal {
    @ColumnInfo(name = "itemId")
    public long itemId;

    @ColumnInfo(name = "total")
    public int total;
}
