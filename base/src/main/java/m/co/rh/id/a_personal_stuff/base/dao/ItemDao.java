package m.co.rh.id.a_personal_stuff.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;

@Dao
public abstract class ItemDao {

    @Transaction
    public void insertItem(ItemState itemState) {
        Item item = itemState.getItem();
        if (item != null) {
            Long itemId = insert(item);
            item.id = itemId;
            List<ItemImage> itemImages = itemState.getItemImages();
            if (itemImages != null && !itemImages.isEmpty()) {
                for (ItemImage itemImage : itemImages) {
                    itemImage.itemId = itemId;
                    itemImage.id = insert(itemImage);
                }
            }
            Collection<ItemTag> itemTags = itemState.getItemTags();
            if (itemTags != null && !itemTags.isEmpty()) {
                for (ItemTag itemTag : itemTags) {
                    itemTag.itemId = itemId;
                    itemTag.id = insert(itemTag);
                }
            }
        }
    }

    @Transaction
    public void updateItem(ItemState itemState) {
        Item item = itemState.getItem();
        if (item != null) {
            update(item);
        }
    }

    @Transaction
    public void deleteItem(ItemState itemState) {
        Item item = itemState.getItem();
        if (item != null) {
            delete(item);
            deleteItemImagesByItemId(item.id);
            deleteItemTagsByItemId(item.id);
        }
    }

    @Query("DELETE FROM item_image WHERE item_id = :itemId")
    protected abstract void deleteItemImagesByItemId(long itemId);

    @Query("DELETE FROM item_tag WHERE item_id = :itemId")
    protected abstract void deleteItemTagsByItemId(long itemId);

    @Insert
    protected abstract long insert(Item item);

    @Update
    protected abstract void update(Item item);

    @Delete
    protected abstract void delete(Item item);

    public List<ItemState> searchItemState(String search) {
        return prepareItemState(searchItem(search));
    }

    private List<ItemState> prepareItemState(List<Item> items) {
        List<ItemState> itemStates = new ArrayList<>();
        if (!items.isEmpty()) {
            for (Item item : items) {
                ItemState itemState = new ItemState();
                itemState.updateItem(item);
                List<ItemImage> itemImages = findItemImagesByItemId(item.id);
                if (!itemImages.isEmpty()) {
                    itemState.updateItemImages(itemImages);
                }
                List<ItemTag> itemTags = findItemTagsByItemId(item.id);
                if (!itemTags.isEmpty()) {
                    itemState.updateItemTags(itemTags);
                }
                itemStates.add(itemState);
            }
        }
        return itemStates;
    }

    @Query("SELECT * FROM item_tag WHERE item_id = :itemId")
    protected abstract List<ItemTag> findItemTagsByItemId(Long itemId);

    @Query("SELECT * FROM item WHERE name LIKE '%'||:search||'%'" +
            " OR description LIKE '%'||:search||'%'" +
            " OR barcode LIKE '%'||:search||'%'")
    public abstract List<Item> searchItem(String search);

    @Query("SELECT * FROM item_tag WHERE tag LIKE '%'||:search||'%'")
    public abstract List<ItemTag> searchItemTag(String search);

    public List<ItemState> findItemStateWithLimit(int limit) {
        return prepareItemState(findItemsWithLimit(limit));
    }

    @Query("SELECT * FROM item ORDER BY" +
            " expired_date_time DESC, updated_date_time DESC, created_date_time DESC" +
            " LIMIT :limit ")
    public abstract List<Item> findItemsWithLimit(int limit);

    @Insert
    protected abstract long insert(ItemImage itemImage);

    @Delete
    protected abstract void delete(ItemImage itemImage);

    @Query("SELECT * FROM item_image WHERE item_id = :itemId ORDER BY created_date_time ASC")
    public abstract List<ItemImage> findItemImagesByItemId(long itemId);

    @Transaction
    public void deleteItemImages(List<ItemImage> itemImages) {
        if (itemImages != null && !itemImages.isEmpty()) {
            for (ItemImage itemImage : itemImages) {
                delete(itemImage);
            }
        }
    }

    public List<ItemState> findItemStatesByIds(List<Long> itemId) {
        return prepareItemState(findItemByIds(itemId));
    }

    @Query("SELECT * FROM item WHERE id IN (:ids)")
    protected abstract List<Item> findItemByIds(List<Long> ids);

    @Transaction
    public void insertItemImage(ItemImage itemImage) {
        itemImage.id = insert(itemImage);
    }

    @Query("SELECT * FROM item_image WHERE file_name = :fileName")
    public abstract ItemImage findItemImageByFileName(String fileName);

    @Insert
    public abstract long insert(ItemTag itemTag);

    @Delete
    public abstract void delete(ItemTag itemTag);

    @Transaction
    public void insertTag(ItemTag itemTag) {
        itemTag.id = insert(itemTag);
    }

    @Query("SELECT * FROM item WHERE id = :itemId")
    public abstract Item finditemById(long itemId);
}
