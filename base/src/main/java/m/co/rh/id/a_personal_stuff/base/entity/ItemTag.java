package m.co.rh.id.a_personal_stuff.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_tag")
public class ItemTag implements Comparable<ItemTag>, Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Item.id
     */
    @ColumnInfo(name = "item_id")
    public Long itemId;

    @ColumnInfo(name = "tag")
    public String tag;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public ItemTag() {
        createdDateTime = new Date();
    }

    @Override
    public int compareTo(ItemTag itemTag) {
        if (tag != null) {
            return tag.compareTo(itemTag.tag);
        }
        return 0;
    }

    @Override
    public ItemTag clone() {
        try {
            return (ItemTag) super.clone();
        } catch (CloneNotSupportedException exception) {
            return null;
        }
    }
}
