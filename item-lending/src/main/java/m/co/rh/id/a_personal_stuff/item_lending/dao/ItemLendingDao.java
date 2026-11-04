package m.co.rh.id.a_personal_stuff.item_lending.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLending;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLendingImage;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;

@Dao
public abstract class ItemLendingDao {
    @Insert
    public abstract long insert(ItemLending itemLending);

    @Update
    public abstract void update(ItemLending itemLending);

    @Delete
    protected abstract void delete(ItemLending itemLending);

    @Transaction
    public void insertItemLending(ItemLendingState itemLendingState) {
        ItemLending itemLending = itemLendingState.getItemLending();
        if (itemLending != null) {
            Long itemLendingId = insert(itemLending);
            itemLending.id = itemLendingId;
            Collection<ItemLendingImage> itemLendingImages = itemLendingState.getItemLendingImages();
            if (!itemLendingImages.isEmpty()) {
                for (ItemLendingImage itemLendingImage : itemLendingImages) {
                    itemLendingImage.itemLendingId = itemLendingId;
                    itemLendingImage.id = insert(itemLendingImage);
                }
            }
        }
    }

    @Transaction
    public void updateItemLending(ItemLendingState itemLendingState) {
        ItemLending itemLending = itemLendingState.getItemLending();
        if (itemLending != null) {
            update(itemLending);
        }
    }

    @Transaction
    public void deleteItemLending(ItemLendingState itemLendingState) {
        ItemLending itemLending = itemLendingState.getItemLending();
        if (itemLending != null) {
            deleteItemLending(itemLending);
        }
    }

    private void deleteItemLending(ItemLending itemLending) {
        delete(itemLending);
        deleteItemLendingImagesByItemLendingId(itemLending.id);
    }

    @Query("DELETE FROM item_lending_image WHERE item_lending_id = :itemLendingId")
    protected abstract void deleteItemLendingImagesByItemLendingId(long itemLendingId);

    @Insert
    public abstract long insert(ItemLendingImage itemLendingImage);

    @Delete
    public abstract void delete(ItemLendingImage itemLendingImage);

    @Query("SELECT * FROM item_lending_image WHERE file_name = :fileName")
    public abstract ItemLendingImage findItemLendingImageByFileName(String fileName);

    public List<ItemLendingState> findItemLendingStateByItemIdWithLimit(long itemId, int limit) {
        return prepareItemLendingState(findItemLendingsByItemIdWithLimit(itemId, limit));
    }

    private List<ItemLendingState> prepareItemLendingState(List<ItemLending> itemLendings) {
        List<ItemLendingState> resultList = new ArrayList<>();
        if (!itemLendings.isEmpty()) {
            for (ItemLending itemLending : itemLendings) {
                ItemLendingState itemLendingState = new ItemLendingState();
                itemLendingState.updateItemLending(itemLending);
                List<ItemLendingImage> itemLendingImages = findItemLendingImagesByItemLendingId(itemLending.id);
                if (!itemLendingImages.isEmpty()) {
                    itemLendingState.updateItemLendingImages(itemLendingImages);
                }
                resultList.add(itemLendingState);
            }
        }
        return resultList;
    }

    @Query("SELECT * FROM item_lending_image WHERE item_lending_id = :itemLendingId")
    protected abstract List<ItemLendingImage> findItemLendingImagesByItemLendingId(Long itemLendingId);

    @Query("SELECT * FROM item_lending WHERE item_id = :itemId ORDER BY" +
            " lend_date_time DESC" +
            " LIMIT :limit ")
    public abstract List<ItemLending> findItemLendingsByItemIdWithLimit(long itemId, int limit);

    public List<ItemLendingState> searchItemLendingStateByItemId(long itemId, String search) {
        return prepareItemLendingState(searchItemLendingByItemId(itemId, search));
    }

    @Query("SELECT * FROM item_lending WHERE item_id = :itemId AND borrower_name LIKE '%'||:search||'%'")
    protected abstract List<ItemLending> searchItemLendingByItemId(long itemId, String search);

    @Transaction
    public void insertItemLendingImage(ItemLendingImage itemLendingImage) {
        itemLendingImage.id = insert(itemLendingImage);
    }

    @Transaction
    public void deleteItemLendingImage(ItemLendingImage itemLendingImage) {
        delete(itemLendingImage);
    }

    public ItemLendingState findItemLendingStateById(long id) {
        // null when the row is gone (e.g. swept with its item): the return
        // notification worker relies on this to no-op on a stale reminder
        ItemLending itemLending = findItemLendingById(id);
        if (itemLending == null) {
            return null;
        }
        List<ItemLendingState> result = prepareItemLendingState(Collections.singletonList(itemLending));
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    @Query("SELECT * FROM item_lending WHERE id = :id")
    public abstract ItemLending findItemLendingById(long id);

    @Transaction
    public void deleteItemLendingStatesByItemId(long itemId) {
        deleteItemLendingStatesByItemIds(Collections.singletonList(itemId));
    }

    // delete itemlendings and all their dependencies: the image statement runs
    // first because its subquery reads the parent table, so parents must
    // still exist when it runs
    @Query("DELETE FROM item_lending_image WHERE item_lending_id IN" +
            " (SELECT id FROM item_lending WHERE item_id IN (:itemIds))")
    protected abstract void deleteItemLendingImagesByItemIds(List<Long> itemIds);

    @Query("DELETE FROM item_lending WHERE item_id IN (:itemIds)")
    protected abstract void deleteItemLendingsByItemIds(List<Long> itemIds);

    @Transaction
    public void deleteItemLendingStatesByItemIds(List<Long> itemIds) {
        deleteItemLendingImagesByItemIds(itemIds);
        deleteItemLendingsByItemIds(itemIds);
    }

    @Query("SELECT * FROM item_lending WHERE item_id = :itemId")
    public abstract List<ItemLending> findItemLendingByItemId(long itemId);

    // used by the startup orphan sweep to compute referenced child ids
    @Query("SELECT DISTINCT item_id FROM item_lending WHERE item_id IS NOT NULL")
    public abstract List<Long> findDistinctItemIds();

    // badge: number of currently open (unreturned) lendings of one item
    @Query("SELECT COUNT(*) FROM item_lending WHERE item_id = :itemId" +
            " AND returned_date_time IS NULL")
    public abstract int countOpenLendingsByItemId(long itemId);

    @Query("SELECT * FROM item_lending")
    public abstract List<ItemLending> findAllItemLendings();

    @Query("SELECT * FROM item_lending_image ORDER BY created_date_time ASC, id ASC")
    public abstract List<ItemLendingImage> findAllItemLendingImages();

    // keyset pagination for bounded-memory exports
    @Query("SELECT * FROM item_lending WHERE id > :lastId ORDER BY id ASC LIMIT :limit")
    public abstract List<ItemLending> findItemLendingsAfter(long lastId, int limit);

    @Query("SELECT * FROM item_lending_image WHERE item_lending_id IN (:itemLendingIds) ORDER BY created_date_time ASC, id ASC")
    public abstract List<ItemLendingImage> findItemLendingImagesByItemLendingIds(List<Long> itemLendingIds);
}
