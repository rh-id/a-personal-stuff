package m.co.rh.id.a_personal_stuff.item_maintenance.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_maintenance_image")
public class ItemMaintenanceImage implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * ItemMaintenance.id
     */
    @ColumnInfo(name = "item_maintenance_id")
    public Long itemMaintenanceId;

    @ColumnInfo(name = "file_name")
    public String fileName;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public ItemMaintenanceImage() {
        createdDateTime = new Date();
    }

    @Override
    public ItemMaintenanceImage clone() {
        try {
            return (ItemMaintenanceImage) super.clone();
        } catch (CloneNotSupportedException exception) {
            return null;
        }
    }
}
