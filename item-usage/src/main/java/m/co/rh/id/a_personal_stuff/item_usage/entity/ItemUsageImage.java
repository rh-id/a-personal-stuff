package m.co.rh.id.a_personal_stuff.item_usage.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_usage_image")
public class ItemUsageImage implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * ItemUsage.id
     */
    @ColumnInfo(name = "item_usage_id")
    public Long itemUsageId;

    @ColumnInfo(name = "file_name")
    public String fileName;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public ItemUsageImage() {
        createdDateTime = new Date();
    }
}
