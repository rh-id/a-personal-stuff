package m.co.rh.id.a_personal_stuff.item_purchase.dao;

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

import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseTotal;

@Dao
public abstract class ItemPurchaseDao {
    @Insert
    public abstract long insert(ItemPurchase itemPurchase);

    @Update
    public abstract void update(ItemPurchase itemPurchase);

    @Delete
    protected abstract void delete(ItemPurchase itemPurchase);

    @Transaction
    public void insertItemPurchase(ItemPurchaseState itemPurchaseState) {
        ItemPurchase itemPurchase = itemPurchaseState.getItemPurchase();
        if (itemPurchase != null) {
            Long itemPurchaseId = insert(itemPurchase);
            itemPurchase.id = itemPurchaseId;
            Collection<ItemPurchaseImage> itemPurchaseImages = itemPurchaseState.getItemPurchaseImages();
            if (!itemPurchaseImages.isEmpty()) {
                for (ItemPurchaseImage itemPurchaseImage : itemPurchaseImages) {
                    itemPurchaseImage.itemPurchaseId = itemPurchaseId;
                    itemPurchaseImage.id = insert(itemPurchaseImage);
                }
            }
        }
    }

    @Transaction
    public void updateItemPurchase(ItemPurchaseState itemPurchaseState) {
        ItemPurchase itemPurchase = itemPurchaseState.getItemPurchase();
        if (itemPurchase != null) {
            update(itemPurchase);
        }
    }

    @Transaction
    public void deleteItemPurchase(ItemPurchaseState itemPurchaseState) {
        ItemPurchase itemPurchase = itemPurchaseState.getItemPurchase();
        if (itemPurchase != null) {
            deleteItemPurchase(itemPurchase);
        }
    }

    private void deleteItemPurchase(ItemPurchase itemPurchase) {
        delete(itemPurchase);
        deleteItemPurchaseImagesByItemPurchaseId(itemPurchase.id);
    }

    @Query("DELETE FROM item_purchase_image WHERE item_purchase_id = :itemPurchaseId")
    protected abstract void deleteItemPurchaseImagesByItemPurchaseId(long itemPurchaseId);

    @Insert
    public abstract long insert(ItemPurchaseImage itemPurchaseImage);

    @Delete
    public abstract void delete(ItemPurchaseImage itemPurchaseImage);

    @Query("SELECT * FROM item_purchase_image WHERE file_name = :fileName")
    public abstract ItemPurchaseImage findItemPurchaseImageByFileName(String fileName);

    public List<ItemPurchaseState> findItemPurchaseStateByItemIdWithLimit(long itemId, int limit) {
        return prepareItemPurchaseState(findItemPurchasesByItemIdWithLimit(itemId, limit));
    }

    private List<ItemPurchaseState> prepareItemPurchaseState(List<ItemPurchase> itemPurchases) {
        List<ItemPurchaseState> resultList = new ArrayList<>();
        if (!itemPurchases.isEmpty()) {
            for (ItemPurchase itemPurchase : itemPurchases) {
                ItemPurchaseState itemPurchaseState = new ItemPurchaseState();
                itemPurchaseState.updateItemPurchase(itemPurchase);
                List<ItemPurchaseImage> itemPurchaseImages = findItemPurchaseImagesByItemPurchaseId(itemPurchase.id);
                if (!itemPurchaseImages.isEmpty()) {
                    itemPurchaseState.updateItemPurchaseImages(itemPurchaseImages);
                }
                resultList.add(itemPurchaseState);
            }
        }
        return resultList;
    }

    @Query("SELECT * FROM item_purchase_image WHERE item_purchase_id = :itemPurchaseId")
    protected abstract List<ItemPurchaseImage> findItemPurchaseImagesByItemPurchaseId(Long itemPurchaseId);

    @Query("SELECT * FROM item_purchase WHERE item_id = :itemId ORDER BY" +
            " purchase_date_time DESC" +
            " LIMIT :limit ")
    public abstract List<ItemPurchase> findItemPurchasesByItemIdWithLimit(long itemId, int limit);

    public List<ItemPurchaseState> searchItemPurchaseStateByItemId(long itemId, String search) {
        return prepareItemPurchaseState(searchItemPurchaseByItemId(itemId, search));
    }

    @Query("SELECT * FROM item_purchase WHERE item_id = :itemId AND description LIKE '%'||:search||'%'")
    protected abstract List<ItemPurchase> searchItemPurchaseByItemId(long itemId, String search);

    @Transaction
    public void insertItemPurchaseImage(ItemPurchaseImage itemPurchaseImage) {
        itemPurchaseImage.id = insert(itemPurchaseImage);
    }

    @Transaction
    public void deleteItemPurchaseImage(ItemPurchaseImage itemPurchaseImage) {
        delete(itemPurchaseImage);
    }

    public ItemPurchaseState findItemPurchaseStateById(long id) {
        List<ItemPurchaseState> result = prepareItemPurchaseState(Collections.singletonList(findItemPurchaseById(id)));
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    @Query("SELECT * FROM item_purchase WHERE id = :id")
    protected abstract ItemPurchase findItemPurchaseById(long id);

    @Transaction
    public void deleteItemPurchaseStatesByItemId(long itemId) {
        deleteItemPurchaseStatesByItemIds(Collections.singletonList(itemId));
    }

    // delete itempurchases and all their dependencies: the image statement
    // runs first because its subquery reads the parent table, so parents must
    // still exist when it runs
    @Query("DELETE FROM item_purchase_image WHERE item_purchase_id IN" +
            " (SELECT id FROM item_purchase WHERE item_id IN (:itemIds))")
    protected abstract void deleteItemPurchaseImagesByItemIds(List<Long> itemIds);

    @Query("DELETE FROM item_purchase WHERE item_id IN (:itemIds)")
    protected abstract void deleteItemPurchasesByItemIds(List<Long> itemIds);

    @Transaction
    public void deleteItemPurchaseStatesByItemIds(List<Long> itemIds) {
        deleteItemPurchaseImagesByItemIds(itemIds);
        deleteItemPurchasesByItemIds(itemIds);
    }

    @Query("SELECT * FROM item_purchase WHERE item_id = :itemId")
    public abstract List<ItemPurchase> findItemPurchaseByItemId(long itemId);

    // used by the startup orphan sweep to compute referenced child ids
    @Query("SELECT DISTINCT item_id FROM item_purchase WHERE item_id IS NOT NULL")
    public abstract List<Long> findDistinctItemIds();

    @Query("SELECT * FROM item_purchase")
    public abstract List<ItemPurchase> findAllItemPurchases();

    @Query("SELECT * FROM item_purchase_image ORDER BY created_date_time ASC, id ASC")
    public abstract List<ItemPurchaseImage> findAllItemPurchaseImages();

    // keyset pagination for bounded-memory exports
    @Query("SELECT * FROM item_purchase WHERE id > :lastId ORDER BY id ASC LIMIT :limit")
    public abstract List<ItemPurchase> findItemPurchasesAfter(long lastId, int limit);

    @Query("SELECT * FROM item_purchase_image WHERE item_purchase_id IN (:itemPurchaseIds) ORDER BY created_date_time ASC, id ASC")
    public abstract List<ItemPurchaseImage> findItemPurchaseImagesByItemPurchaseIds(List<Long> itemPurchaseIds);

    @Query("SELECT item_id AS itemId, SUM(amount) AS total FROM item_purchase GROUP BY item_id")
    public abstract List<ItemPurchaseTotal> sumAmountGroupByItem();

    // one earliest dated purchase per item: the correlated subquery ranks a
    // row against its item's other purchases (newest-first like
    // findItemPurchasesByItemIdWithLimit is the wrong direction for a "first
    // purchase" report column) with id ASC as the deterministic tie-break so
    // two purchases sharing a timestamp resolve stably. Items without any
    // dated purchase simply have no row.
    @Query("SELECT * FROM item_purchase WHERE id = (" +
            "SELECT p2.id FROM item_purchase p2" +
            " WHERE p2.item_id = item_purchase.item_id" +
            " AND p2.purchase_date_time IS NOT NULL" +
            " ORDER BY p2.purchase_date_time ASC, p2.id ASC" +
            " LIMIT 1)")
    public abstract List<ItemPurchase> findFirstPurchasesPerItem();
}
