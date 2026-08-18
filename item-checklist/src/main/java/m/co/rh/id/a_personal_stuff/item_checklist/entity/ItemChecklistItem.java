package m.co.rh.id.a_personal_stuff.item_checklist.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_checklist_item",
        indices = {@Index(value = {"item_checklist_id", "item_id"}, unique = true)})
public class ItemChecklistItem implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    @ColumnInfo(name = "item_checklist_id")
    public Long itemChecklistId;

    @ColumnInfo(name = "item_id")
    public Long itemId;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "checked_date_time")
    public Date checkedDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public ItemChecklistItem() {
        createdDateTime = new Date();
    }

    @Override
    public ItemChecklistItem clone() {
        try {
            return (ItemChecklistItem) super.clone();
        } catch (CloneNotSupportedException exception) {
            return null;
        }
    }
}
