package m.co.rh.id.a_personal_stuff.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_image")
public class ItemImage implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Item.id
     */
    @ColumnInfo(name = "item_id")
    public Long itemId;

    @ColumnInfo(name = "file_name")
    public String fileName;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public ItemImage() {
        createdDateTime = new Date();
    }
}
