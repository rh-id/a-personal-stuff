package m.co.rh.id.a_personal_stuff.item_usage.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;

@Database(entities = {ItemUsage.class, ItemUsageImage.class},
        version = 1)
public abstract class ItemUsageDatabase extends RoomDatabase {
    public abstract ItemUsageDao itemUsageDao();
}
