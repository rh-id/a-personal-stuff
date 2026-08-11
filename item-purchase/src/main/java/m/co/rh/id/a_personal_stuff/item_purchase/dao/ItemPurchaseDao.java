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

    // delete itempurchase and all its dependencies
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
        List<ItemPurchase> itemPurchases = findItemPurchaseByItemId(itemId);
        if (!itemPurchases.isEmpty()) {
            for (ItemPurchase itemPurchase : itemPurchases) {
                deleteItemPurchase(itemPurchase);
            }
        }
    }

    @Query("SELECT * FROM item_purchase WHERE item_id = :itemId")
    public abstract List<ItemPurchase> findItemPurchaseByItemId(long itemId);

    @Query("SELECT * FROM item_purchase")
    public abstract List<ItemPurchase> findAllItemPurchases();

    @Query("SELECT * FROM item_purchase_image")
    public abstract List<ItemPurchaseImage> findAllItemPurchaseImages();
}
