package m.co.rh.id.a_personal_stuff.item_checklist.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistProgress;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;

@Dao
public abstract class ItemChecklistDao {
    public enum QueryOrderBy {
        UPDATED_DATE_TIME_ASC,
        UPDATED_DATE_TIME_DESC,
        CREATED_DATE_TIME_ASC,
        CREATED_DATE_TIME_DESC
    }

    @Insert
    protected abstract long insert(ItemChecklist itemChecklist);

    @Update
    protected abstract void update(ItemChecklist itemChecklist);

    @Delete
    protected abstract void delete(ItemChecklist itemChecklist);

    @Transaction
    public void insertItemChecklist(ItemChecklistState state) {
        ItemChecklist itemChecklist = state.getItemChecklist();
        if (itemChecklist != null) {
            if (itemChecklist.createdDateTime == null) {
                itemChecklist.createdDateTime = new Date();
            }
            if (itemChecklist.updatedDateTime == null) {
                itemChecklist.updatedDateTime = new Date();
            }
            Long itemChecklistId = insert(itemChecklist);
            itemChecklist.id = itemChecklistId;
            Collection<ItemChecklistItem> itemChecklistItems = state.getItemChecklistItems();
            if (!itemChecklistItems.isEmpty()) {
                for (ItemChecklistItem itemChecklistItem : itemChecklistItems) {
                    itemChecklistItem.itemChecklistId = itemChecklistId;
                    itemChecklistItem.id = insert(itemChecklistItem);
                }
            }
        }
    }

    @Transaction
    public void updateItemChecklist(ItemChecklistState state) {
        ItemChecklist itemChecklist = state.getItemChecklist();
        if (itemChecklist != null) {
            update(itemChecklist);
        }
    }

    @Transaction
    public void updateItemChecklist(ItemChecklist itemChecklist) {
        if (itemChecklist != null) {
            update(itemChecklist);
        }
    }

    @Transaction
    public void deleteItemChecklist(ItemChecklistState state) {
        ItemChecklist itemChecklist = state.getItemChecklist();
        if (itemChecklist != null) {
            deleteItemChecklistItemsByChecklistId(itemChecklist.id);
            delete(itemChecklist);
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract List<Long> insertItemChecklistItems(List<ItemChecklistItem> items);

    @Update
    public abstract void updateItemChecklistItem(ItemChecklistItem item);

    @Delete
    public abstract void deleteItemChecklistItem(ItemChecklistItem item);

    @Query("DELETE FROM item_checklist_item WHERE item_checklist_id = :checklistId")
    protected abstract void deleteItemChecklistItemsByChecklistId(long checklistId);

    @Query("DELETE FROM item_checklist_item WHERE item_id = :itemId")
    public abstract void deleteItemChecklistItemsByItemId(long itemId);

    public List<ItemChecklistState> findItemChecklistWithLimit(int limit, QueryOrderBy orderBy) {
        Supplier<List<ItemChecklist>> itemSupplier;

        if (orderBy == null) {
            itemSupplier = () -> findItemChecklistsWithLimit(limit);
        } else {
            switch (orderBy) {
                case UPDATED_DATE_TIME_ASC:
                    itemSupplier = () -> findItemChecklistsWithLimit_orderByUpdatedDateTime(limit);
                    break;
                case UPDATED_DATE_TIME_DESC:
                    itemSupplier = () -> findItemChecklistsWithLimit_orderByUpdatedDateTimeDesc(limit);
                    break;
                case CREATED_DATE_TIME_ASC:
                    itemSupplier = () -> findItemChecklistsWithLimit_orderByCreatedDateTime(limit);
                    break;
                case CREATED_DATE_TIME_DESC:
                    itemSupplier = () -> findItemChecklistsWithLimit_orderByCreatedDateTimeDesc(limit);
                    break;
                default:
                    itemSupplier = () -> findItemChecklistsWithLimit(limit);
            }
        }
        return prepareItemChecklistState(itemSupplier.get());
    }

    @Query("SELECT * FROM item_checklist ORDER BY updated_date_time DESC, created_date_time DESC LIMIT :limit")
    public abstract List<ItemChecklist> findItemChecklistsWithLimit(int limit);

    @Query("SELECT * FROM item_checklist ORDER BY updated_date_time ASC LIMIT :limit")
    public abstract List<ItemChecklist> findItemChecklistsWithLimit_orderByUpdatedDateTime(int limit);

    @Query("SELECT * FROM item_checklist ORDER BY updated_date_time DESC LIMIT :limit")
    public abstract List<ItemChecklist> findItemChecklistsWithLimit_orderByUpdatedDateTimeDesc(int limit);

    @Query("SELECT * FROM item_checklist ORDER BY created_date_time ASC LIMIT :limit")
    public abstract List<ItemChecklist> findItemChecklistsWithLimit_orderByCreatedDateTime(int limit);

    @Query("SELECT * FROM item_checklist ORDER BY created_date_time DESC LIMIT :limit")
    public abstract List<ItemChecklist> findItemChecklistsWithLimit_orderByCreatedDateTimeDesc(int limit);

    @Query("SELECT * FROM item_checklist WHERE title LIKE '%'||:search||'%' OR description LIKE '%'||:search||'%'")
    public abstract List<ItemChecklist> searchItemChecklist(String search);

    @Query("SELECT * FROM item_checklist_item WHERE item_checklist_id = :checklistId ORDER BY created_date_time ASC, id ASC")
    public abstract List<ItemChecklistItem> findItemChecklistItemsByChecklistId(long checklistId);

    @Query("SELECT * FROM item_checklist_item WHERE id = :id")
    public abstract ItemChecklistItem findItemChecklistItemById(long id);

    @Query("SELECT item_id FROM item_checklist_item WHERE item_checklist_id = :checklistId")
    public abstract List<Long> findExistingItemIds(long checklistId);

    @Query("SELECT item_checklist_id as itemChecklistId, COUNT(*) as total, SUM(CASE WHEN checked_date_time IS NOT NULL THEN 1 ELSE 0 END) as checked FROM item_checklist_item GROUP BY item_checklist_id")
    public abstract List<ItemChecklistProgress> findAllItemChecklistProgress();

    @Query("SELECT item_checklist_id as itemChecklistId, COUNT(*) as total, SUM(CASE WHEN checked_date_time IS NOT NULL THEN 1 ELSE 0 END) as checked FROM item_checklist_item WHERE item_checklist_id = :checklistId GROUP BY item_checklist_id")
    public abstract ItemChecklistProgress findItemChecklistProgressByChecklistId(long checklistId);

    @Query("SELECT COUNT(*) FROM item_checklist_item WHERE item_id = :itemId")
    public abstract int countItemChecklistItemsByItemId(long itemId);

    @Query("SELECT * FROM item_checklist WHERE id IN (SELECT item_checklist_id FROM item_checklist_item WHERE item_id = :itemId) ORDER BY updated_date_time DESC, created_date_time DESC")
    protected abstract List<ItemChecklist> findItemChecklistsByItemId(long itemId);

    @Query("SELECT * FROM item_checklist")
    public abstract List<ItemChecklist> findAllItemChecklists();

    @Query("SELECT * FROM item_checklist WHERE id NOT IN (SELECT item_checklist_id FROM item_checklist_item WHERE item_id = :itemId) AND (title LIKE '%'||:search||'%' OR description LIKE '%'||:search||'%') ORDER BY updated_date_time DESC, created_date_time DESC")
    protected abstract List<ItemChecklist> findItemChecklistsNotContainingItem(long itemId, String search);

    @Query("SELECT * FROM item_checklist_item")
    public abstract List<ItemChecklistItem> findAllItemChecklistItems();

    // keyset pagination for bounded-memory exports
    @Query("SELECT * FROM item_checklist_item WHERE id > :lastId ORDER BY id ASC LIMIT :limit")
    public abstract List<ItemChecklistItem> findItemChecklistItemsAfter(long lastId, int limit);

    @Insert
    protected abstract long insert(ItemChecklistItem itemChecklistItem);

    @Transaction
    public void updateItemChecklistItem(ItemChecklistItem item, ItemChecklist checklist) {
        updateItemChecklistItem(item);
        if (checklist != null) {
            checklist.updatedDateTime = new Date();
            update(checklist);
        }
    }

    private List<ItemChecklistState> prepareItemChecklistState(List<ItemChecklist> itemChecklists) {
        List<ItemChecklistState> itemChecklistStates = new ArrayList<>();
        if (!itemChecklists.isEmpty()) {
            for (ItemChecklist itemChecklist : itemChecklists) {
                ItemChecklistState itemChecklistState = new ItemChecklistState();
                itemChecklistState.updateItemChecklist(itemChecklist);
                List<ItemChecklistItem> itemChecklistItems = findItemChecklistItemsByChecklistId(itemChecklist.id);
                if (!itemChecklistItems.isEmpty()) {
                    itemChecklistState.updateItemChecklistItems(itemChecklistItems);
                }
                itemChecklistStates.add(itemChecklistState);
            }
        }
        return itemChecklistStates;
    }

    public ItemChecklistState findItemChecklistStateById(long id) {
        List<ItemChecklistState> result = prepareItemChecklistState(Collections.singletonList(findItemChecklistById(id)));
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    public List<ItemChecklistState> findItemChecklistStatesByItemId(long itemId) {
        return prepareItemChecklistState(findItemChecklistsByItemId(itemId));
    }

    public List<ItemChecklistState> findItemChecklistStatesNotContainingItem(long itemId, String search) {
        return prepareItemChecklistState(findItemChecklistsNotContainingItem(itemId, search));
    }

    @Query("SELECT * FROM item_checklist WHERE id = :id")
    protected abstract ItemChecklist findItemChecklistById(long id);
}
