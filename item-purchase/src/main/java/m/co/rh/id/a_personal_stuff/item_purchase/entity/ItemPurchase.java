package m.co.rh.id.a_personal_stuff.item_purchase.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_purchase")
public class ItemPurchase implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Item.id
     */
    @ColumnInfo(name = "item_id")
    public Long itemId;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "amount")
    public int amount;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "cost")
    public BigDecimal cost;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "purchase_date_time")
    public Date purchaseDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public ItemPurchase() {
        Date date = new Date();
        purchaseDateTime = date;
        createdDateTime = date;
    }

    @Override
    public ItemPurchase clone() {
        try {
            return (ItemPurchase) super.clone();
        } catch (CloneNotSupportedException exception) {
            return null;
        }
    }
}
