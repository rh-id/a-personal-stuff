package m.co.rh.id.a_personal_stuff.item_checklist.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Transaction;
import androidx.room.Update;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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

    @Query("DELETE FROM item_checklist_item WHERE item_id IN (:itemIds)")
    public abstract void deleteItemChecklistItemsByItemIds(List<Long> itemIds);

    /**
     * Un-checks the given rows of one checklist (regeneration for the
     * designated shopping list): a re-tap must move already-checked low-stock
     * items back to the open section, otherwise a fully-checked stale list
     * would never surface its restock reminders again. Returns the number of
     * rows actually cleared so the caller only bumps/notifies when something
     * changed. Same plain IN clause as
     * {@link #deleteItemChecklistItemsByItemIds(List)} — low-stock result
     * sets are small.
     */
    @Query("UPDATE item_checklist_item SET checked_date_time = NULL WHERE item_checklist_id = :checklistId AND item_id IN (:itemIds) AND checked_date_time IS NOT NULL")
    public abstract int uncheckItems(long checklistId, List<Long> itemIds);

    // used by the startup orphan sweep to compute referenced child ids
    @Query("SELECT DISTINCT item_id FROM item_checklist_item WHERE item_id IS NOT NULL")
    public abstract List<Long> findDistinctItemIds();

    public List<ItemChecklistState> findItemChecklistWithLimit(int limit, QueryOrderBy orderBy) {
        String sql = "SELECT * FROM item_checklist" + orderClause(orderBy) + " LIMIT ?";
        return prepareItemChecklistState(
                findItemChecklistRaw(new SimpleSQLiteQuery(sql, new Object[]{limit})));
    }

    @RawQuery
    protected abstract List<ItemChecklist> findItemChecklistRaw(SupportSQLiteQuery query);

    /**
     * ORDER BY fragment per {@link QueryOrderBy}, identical to the former
     * hand-written @Query sort matrix: the null/default composite is
     * updated DESC, created DESC and the single-key variants
     * have no tiebreaker. The fragment comes ONLY from this enum - never
     * user input - which is the injection boundary that makes the string
     * concatenation in the @RawQuery wrapper safe.
     */
    private static String orderClause(QueryOrderBy orderBy) {
        if (orderBy == null) {
            return " ORDER BY updated_date_time DESC, created_date_time DESC";
        }
        switch (orderBy) {
            case UPDATED_DATE_TIME_ASC:
                return " ORDER BY updated_date_time ASC";
            case UPDATED_DATE_TIME_DESC:
                return " ORDER BY updated_date_time DESC";
            case CREATED_DATE_TIME_ASC:
                return " ORDER BY created_date_time ASC";
            case CREATED_DATE_TIME_DESC:
                return " ORDER BY created_date_time DESC";
            default:
                return " ORDER BY updated_date_time DESC, created_date_time DESC";
        }
    }

    @Query("SELECT * FROM item_checklist WHERE title LIKE '%'||:search||'%' OR description LIKE '%'||:search||'%'")
    public abstract List<ItemChecklist> searchItemChecklist(String search);

    /**
     * Adopt-once lookup for the designated shopping list: title is NOT unique
     * in the schema, so the most recently updated match wins — that is the
     * list the user actually worked with. Case-insensitive so an exact-title
     * adoption survives locale-driven capitalization differences.
     */
    @Query("SELECT * FROM item_checklist WHERE title = :title COLLATE NOCASE ORDER BY updated_date_time DESC LIMIT 1")
    public abstract ItemChecklist findItemChecklistByTitle(String title);

    /**
     * Cheap existence check for the stored designated-shopping-list id:
     * a stored id is validated here before
     * {@link #findItemChecklistStateById(long)} materializes the full
     * checklist + item-row state (or answers {@code null} for a stale id).
     */
    @Query("SELECT COUNT(*) FROM item_checklist WHERE id = :id")
    public abstract int countItemChecklistsById(long id);

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

    /**
     * Loads one checklist together with its item rows as a state. Returns
     * {@code null} when the checklist row is missing — the state builder
     * must never be fed a null row (RxJava onNext rejects null), so the
     * missing case is answered before any state is materialized.
     */
    public ItemChecklistState findItemChecklistStateById(long id) {
        ItemChecklist itemChecklist = findItemChecklistById(id);
        if (itemChecklist == null) {
            return null;
        }
        List<ItemChecklistState> result = prepareItemChecklistState(Collections.singletonList(itemChecklist));
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
