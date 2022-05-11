package m.co.rh.id.a_personal_stuff.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item")
public class Item implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "amount")
    public int amount;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "price")
    public BigDecimal price;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "barcode")
    public String barcode;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "expired_date_time")
    public Date expiredDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "updated_date_time")
    public Date updatedDateTime;


    public Item() {
        Date date = new Date();
        createdDateTime = date;
        updatedDateTime = date;
    }

    @Override
    public Item clone() {
        try {
            return (Item) super.clone();
        } catch (CloneNotSupportedException exception) {
            return null;
        }
    }
}
