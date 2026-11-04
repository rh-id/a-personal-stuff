package m.co.rh.id.a_personal_stuff.item_lending.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLending;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLendingImage;

@Database(entities = {ItemLending.class, ItemLendingImage.class},
        version = 1)
public abstract class ItemLendingDatabase extends RoomDatabase {
    public abstract ItemLendingDao itemLendingDao();
}
