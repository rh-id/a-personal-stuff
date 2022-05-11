package m.co.rh.id.a_personal_stuff.item_usage.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_usage")
public class ItemUsage implements Serializable, Cloneable {
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
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public ItemUsage() {
        createdDateTime = new Date();
    }

    @Override
    public ItemUsage clone() {
        try {
            return (ItemUsage) super.clone();
        } catch (CloneNotSupportedException exception) {
            return null;
        }
    }
}
