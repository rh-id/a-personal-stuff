package m.co.rh.id.a_personal_stuff.item_reminder.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Entity(tableName = "item_reminder")
public class ItemReminder implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Item.id
     */
    @ColumnInfo(name = "item_id")
    public Long itemId;

    /**
     * Unique UUID for work manager unique work
     */
    @ColumnInfo(name = "task_id")
    public String taskId;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "reminder_date_time")
    public Date reminderDateTime;

    @ColumnInfo(name = "message")
    public String message;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    public ItemReminder() {
        taskId = UUID.randomUUID().toString();
        createdDateTime = new Date();
    }

    @Override
    public ItemReminder clone() {
        try {
            return (ItemReminder) super.clone();
        } catch (CloneNotSupportedException exception) {
            return null;
        }
    }
}
