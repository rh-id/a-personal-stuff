package m.co.rh.id.a_personal_stuff.item_reminder.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;

@Dao
public abstract class ItemReminderDao {
    @Insert
    protected abstract long insert(ItemReminder itemReminder);

    @Delete
    public abstract void delete(List<ItemReminder> itemReminder);

    @Transaction
    public void insertItemReminder(ItemReminder itemReminder) {
        itemReminder.id = insert(itemReminder);
    }

    @Query("SELECT * FROM item_reminder WHERE item_id = :itemId")
    public abstract List<ItemReminder> findItemReminderByItemId(long itemId);

    @Query("SELECT * FROM item_reminder WHERE id = :id")
    public abstract ItemReminder findItemReminderById(long id);

    @Query("SELECT * FROM item_reminder WHERE message LIKE '%'||:search||'%'")
    public abstract List<ItemReminder> searchItemReminderMessage(String search);

    @Query("SELECT * FROM item_reminder WHERE item_id = :itemId LIMIT :limit")
    public abstract List<ItemReminder> findItemReminderByItemIdWithLimit(long itemId, int limit);
}
