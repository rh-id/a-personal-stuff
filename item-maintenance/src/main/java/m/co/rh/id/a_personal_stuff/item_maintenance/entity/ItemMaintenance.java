package m.co.rh.id.a_personal_stuff.item_maintenance.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_maintenance")
public class ItemMaintenance implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Item.id
     */
    @ColumnInfo(name = "item_id")
    public Long itemId;

    @ColumnInfo(name = "description")
    public String description;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "cost")
    public BigDecimal cost;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "maintenance_date_time")
    public Date maintenanceDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public ItemMaintenance() {
        Date date = new Date();
        maintenanceDateTime = date;
        createdDateTime = date;
    }

    @Override
    public ItemMaintenance clone() {
        try {
            return (ItemMaintenance) super.clone();
        } catch (CloneNotSupportedException exception) {
            return null;
        }
    }
}
