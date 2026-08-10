package m.co.rh.id.a_personal_stuff.item_purchase.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_purchase_image")
public class ItemPurchaseImage implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * ItemPurchase.id
     */
    @ColumnInfo(name = "item_purchase_id")
    public Long itemPurchaseId;

    @ColumnInfo(name = "file_name")
    public String fileName;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public ItemPurchaseImage() {
        createdDateTime = new Date();
    }
}
