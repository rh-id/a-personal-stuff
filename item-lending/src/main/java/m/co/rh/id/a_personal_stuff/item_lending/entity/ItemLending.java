package m.co.rh.id.a_personal_stuff.item_lending.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_lending")
public class ItemLending implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Item.id
     */
    @ColumnInfo(name = "item_id")
    public Long itemId;

    @ColumnInfo(name = "borrower_name")
    public String borrowerName;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "lend_date_time")
    public Date lendDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "due_date_time")
    public Date dueDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "returned_date_time")
    public Date returnedDateTime;

    @ColumnInfo(name = "note")
    public String note;

    /**
     * Unique UUID for work manager unique work (the return reminder)
     */
    @ColumnInfo(name = "task_id")
    public String taskId;

    public ItemLending() {
        taskId = UUID.randomUUID().toString();
    }

    @Override
    public ItemLending clone() {
        try {
            return (ItemLending) super.clone();
        } catch (CloneNotSupportedException exception) {
            return null;
        }
    }
}
