package m.co.rh.id.a_personal_stuff.item_checklist.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;

@Database(entities = {ItemChecklist.class, ItemChecklistItem.class}, version = 1)
public abstract class ItemChecklistDatabase extends RoomDatabase {
    public abstract ItemChecklistDao itemChecklistDao();
}
