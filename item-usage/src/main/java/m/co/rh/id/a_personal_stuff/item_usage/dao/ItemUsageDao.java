package m.co.rh.id.a_personal_stuff.item_usage.dao;

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

import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;

@Dao
public abstract class ItemUsageDao {
    @Insert
    public abstract long insert(ItemUsage itemUsage);

    @Update
    public abstract void update(ItemUsage itemUsage);

    @Delete
    protected abstract void delete(ItemUsage itemUsage);

    @Transaction
    public void insertItemUsage(ItemUsageState itemUsageState) {
        ItemUsage itemUsage = itemUsageState.getItemUsage();
        if (itemUsage != null) {
            Long itemUsageId = insert(itemUsage);
            itemUsage.id = itemUsageId;
            Collection<ItemUsageImage> itemUsageImages = itemUsageState.getItemUsageImages();
            if (!itemUsageImages.isEmpty()) {
                for (ItemUsageImage itemUsageImage : itemUsageImages) {
                    itemUsageImage.itemUsageId = itemUsageId;
                    itemUsageImage.id = insert(itemUsageImage);
                }
            }
        }
    }

    @Transaction
    public void updateItemUsage(ItemUsageState itemUsageState) {
        ItemUsage itemUsage = itemUsageState.getItemUsage();
        if (itemUsage != null) {
            update(itemUsage);
        }
    }

    @Transaction
    public void deleteItemUsage(ItemUsageState itemUsageState) {
        ItemUsage itemUsage = itemUsageState.getItemUsage();
        if (itemUsage != null) {
            deleteItemUsage(itemUsage);
        }
    }

    // delete itemusage and all its dependencies
    private void deleteItemUsage(ItemUsage itemUsage) {
        delete(itemUsage);
        deleteItemUsageImagesByItemUsageId(itemUsage.id);
    }

    @Query("DELETE FROM item_usage_image WHERE item_usage_id = :itemUsageId")
    protected abstract void deleteItemUsageImagesByItemUsageId(long itemUsageId);

    @Insert
    public abstract long insert(ItemUsageImage itemUsageImage);

    @Delete
    public abstract void delete(ItemUsageImage itemUsageImage);

    @Query("SELECT * FROM item_usage_image WHERE file_name = :fileName")
    public abstract ItemUsageImage findItemUsageImageByFileName(String fileName);

    public List<ItemUsageState> findItemUsageStateByItemIdWithLimit(long itemId, int limit) {
        return prepareItemUsageState(findItemUsagesByItemIdWithLimit(itemId, limit));
    }

    private List<ItemUsageState> prepareItemUsageState(List<ItemUsage> itemUsages) {
        List<ItemUsageState> resultList = new ArrayList<>();
        if (!itemUsages.isEmpty()) {
            for (ItemUsage itemUsage : itemUsages) {
                ItemUsageState itemUsageState = new ItemUsageState();
                itemUsageState.updateItemUsage(itemUsage);
                List<ItemUsageImage> itemUsageImages = findItemUsageImagesByItemUsageId(itemUsage.id);
                if (!itemUsageImages.isEmpty()) {
                    itemUsageState.updateItemUsageImages(itemUsageImages);
                }
                resultList.add(itemUsageState);
            }
        }
        return resultList;
    }

    @Query("SELECT * FROM item_usage_image WHERE item_usage_id = :itemUsageId")
    protected abstract List<ItemUsageImage> findItemUsageImagesByItemUsageId(Long itemUsageId);

    @Query("SELECT * FROM item_usage WHERE item_id = :itemId ORDER BY" +
            " created_date_time DESC" +
            " LIMIT :limit ")
    public abstract List<ItemUsage> findItemUsagesByItemIdWithLimit(long itemId, int limit);

    public List<ItemUsageState> searchItemUsageStateByItemId(long itemId, String search) {
        return prepareItemUsageState(searchItemUsageByItemId(itemId, search));
    }

    @Query("SELECT * FROM item_usage WHERE item_id = :itemId AND description LIKE '%'||:search||'%'")
    protected abstract List<ItemUsage> searchItemUsageByItemId(long itemId, String search);

    @Transaction
    public void insertItemUsageImage(ItemUsageImage itemUsageImage) {
        itemUsageImage.id = insert(itemUsageImage);
    }

    @Transaction
    public void deleteItemUsageImage(ItemUsageImage itemUsageImage) {
        delete(itemUsageImage);
    }

    public ItemUsageState findItemUsageStateById(long id) {
        List<ItemUsageState> result = prepareItemUsageState(Collections.singletonList(findItemUsageById(id)));
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    @Query("SELECT * FROM item_usage WHERE id = :id")
    protected abstract ItemUsage findItemUsageById(long id);

    @Transaction
    public void deleteItemUsageStatesByItemId(long itemId) {
        List<ItemUsage> itemUsages = findItemUsageByItemId(itemId);
        if (!itemUsages.isEmpty()) {
            for (ItemUsage itemUsage : itemUsages) {
                deleteItemUsage(itemUsage);
            }
        }
    }

    @Query("SELECT * FROM item_usage WHERE item_id = :itemId")
    public abstract List<ItemUsage> findItemUsageByItemId(long itemId);
}
