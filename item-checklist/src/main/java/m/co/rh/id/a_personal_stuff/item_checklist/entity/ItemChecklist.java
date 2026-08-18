package m.co.rh.id.a_personal_stuff.item_checklist.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_checklist")
public class ItemChecklist implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "description")
    public String description;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "updated_date_time")
    public Date updatedDateTime;

    public ItemChecklist() {
        Date date = new Date();
        createdDateTime = date;
        updatedDateTime = date;
    }

    @Override
    public ItemChecklist clone() {
        try {
            return (ItemChecklist) super.clone();
        } catch (CloneNotSupportedException exception) {
            return null;
        }
    }
}
