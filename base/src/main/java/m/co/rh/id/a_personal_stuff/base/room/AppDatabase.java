package m.co.rh.id.a_personal_stuff.base.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_personal_stuff.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.AndroidNotification;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;

@Database(entities = {AndroidNotification.class,
        Item.class, ItemImage.class, ItemTag.class},
        version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AndroidNotificationDao androidNotificationDao();

    public abstract ItemDao itemDao();
}
