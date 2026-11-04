package m.co.rh.id.a_personal_stuff.item_lending.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_lending_image")
public class ItemLendingImage implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * ItemLending.id
     */
    @ColumnInfo(name = "item_lending_id")
    public Long itemLendingId;

    @ColumnInfo(name = "file_name")
    public String fileName;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public ItemLendingImage() {
        createdDateTime = new Date();
    }
}
