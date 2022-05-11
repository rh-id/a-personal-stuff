package m.co.rh.id.a_personal_stuff.item_reminder.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;


@Database(entities = {ItemReminder.class,},
        version = 1)
public abstract class ItemReminderDatabase extends RoomDatabase {
    public abstract ItemReminderDao itemReminderDao();
}
