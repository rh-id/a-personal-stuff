package m.co.rh.id.a_personal_stuff.item_maintenance.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;

@Database(entities = {ItemMaintenance.class, ItemMaintenanceImage.class},
        version = 1)
public abstract class ItemMaintenanceDatabase extends RoomDatabase {
    public abstract ItemMaintenanceDao itemMaintenanceDao();
}
