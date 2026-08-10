package m.co.rh.id.a_personal_stuff.item_purchase.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;

@Database(entities = {ItemPurchase.class, ItemPurchaseImage.class},
        version = 1)
public abstract class ItemPurchaseDatabase extends RoomDatabase {
    public abstract ItemPurchaseDao itemPurchaseDao();
}
