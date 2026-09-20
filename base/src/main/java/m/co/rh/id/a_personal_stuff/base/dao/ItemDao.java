package m.co.rh.id.a_personal_stuff.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.TypeConverters;
import androidx.room.Update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.room.converter.Converter;

@Dao
public abstract class ItemDao {
    /**
     * Maximum number of ids bound per IN(...) clause. SQLite's default
     * bind-variable cap is 999 (900 on some API levels) and the page limit
     * doubles up to 1600 (see PagedItemCmd#loadNextPage), so id lists must
     * be chunked (see also ExportSpreadsheetCmd).
     */
    private static final int SQLITE_IN_CHUNK_SIZE = 500;

    public enum QueryOrderBy {
        EXPIRED_DATE_TIME_ASC,
        EXPIRED_DATE_TIME_DESC,
        UPDATED_DATE_TIME_ASC,
        UPDATED_DATE_TIME_DESC,
        CREATED_DATE_TIME_ASC,
        CREATED_DATE_TIME_DESC,
    }

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
            item.updatedDateTime = new Date();
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

    private List<ItemState> prepareItemState(List<Item> items) {
        if (items.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> itemIds = new ArrayList<>(items.size());
        for (Item item : items) {
            itemIds.add(item.id);
        }
        List<ItemImage> allItemImages = new ArrayList<>();
        List<ItemTag> allItemTags = new ArrayList<>();
        for (List<Long> itemIdsChunk : chunkIds(itemIds)) {
            allItemImages.addAll(findItemImagesByItemIds(itemIdsChunk));
            allItemTags.addAll(findItemTagsByItemIds(itemIdsChunk));
        }
        return assembleItemStates(items, allItemImages, allItemTags);
    }

    /**
     * Pure grouping/assembly of pre-fetched images and tags onto their items,
     * preserving the items-list order and the per-item semantics: images/tags
     * are only attached when that item's list is non-empty. Package-private
     * static so it stays unit-testable without Room.
     */
    static List<ItemState> assembleItemStates(List<Item> items,
                                              List<ItemImage> images,
                                              List<ItemTag> tags) {
        Map<Long, List<ItemImage>> imagesByItemId = new HashMap<>();
        for (ItemImage itemImage : images) {
            List<ItemImage> itemImages = imagesByItemId.get(itemImage.itemId);
            if (itemImages == null) {
                itemImages = new ArrayList<>();
                imagesByItemId.put(itemImage.itemId, itemImages);
            }
            itemImages.add(itemImage);
        }
        Map<Long, List<ItemTag>> tagsByItemId = new HashMap<>();
        for (ItemTag itemTag : tags) {
            List<ItemTag> itemTags = tagsByItemId.get(itemTag.itemId);
            if (itemTags == null) {
                itemTags = new ArrayList<>();
                tagsByItemId.put(itemTag.itemId, itemTags);
            }
            itemTags.add(itemTag);
        }
        List<ItemState> itemStates = new ArrayList<>(items.size());
        for (Item item : items) {
            ItemState itemState = new ItemState();
            itemState.updateItem(item);
            List<ItemImage> itemImages = imagesByItemId.get(item.id);
            if (itemImages != null && !itemImages.isEmpty()) {
                itemState.updateItemImages(itemImages);
            }
            List<ItemTag> itemTags = tagsByItemId.get(item.id);
            if (itemTags != null && !itemTags.isEmpty()) {
                itemState.updateItemTags(itemTags);
            }
            itemStates.add(itemState);
        }
        return itemStates;
    }

    static List<List<Long>> chunkIds(List<Long> ids) {
        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += SQLITE_IN_CHUNK_SIZE) {
            chunks.add(ids.subList(i, Math.min(i + SQLITE_IN_CHUNK_SIZE, ids.size())));
        }
        return chunks;
    }

    @Query("SELECT * FROM item_tag WHERE item_id = :itemId")
    protected abstract List<ItemTag> findItemTagsByItemId(Long itemId);

    @Query("SELECT * FROM item_image WHERE item_id IN (:itemIds)" +
            " ORDER BY created_date_time ASC, id ASC")
    public abstract List<ItemImage> findItemImagesByItemIds(List<Long> itemIds);

    @Query("SELECT * FROM item_tag WHERE item_id IN (:itemIds)")
    public abstract List<ItemTag> findItemTagsByItemIds(List<Long> itemIds);

    @Query("SELECT * FROM item WHERE name LIKE '%'||:search||'%'" +
            " OR description LIKE '%'||:search||'%'" +
            " OR barcode LIKE '%'||:search||'%'")
    public abstract List<Item> searchItem(String search);

    @Query("SELECT * FROM item WHERE barcode LIKE '%'||:search||'%'")
    public abstract List<Item> searchItemBarcode(String search);

    @Query("SELECT * FROM item_tag WHERE tag LIKE '%'||:search||'%'")
    public abstract List<ItemTag> searchItemTag(String search);

    public List<ItemState> findItemStateWithLimit(int limit, QueryOrderBy queryOrderBy) {
        Supplier<List<Item>> itemSupplier;

        if (queryOrderBy == null) {
            itemSupplier = () -> findItemsWithLimit(limit);
        } else {
            switch (queryOrderBy) {
                case EXPIRED_DATE_TIME_ASC:
                    itemSupplier = () -> findItemsWithLimit_orderByExpiredDateTime(limit);
                    break;
                case EXPIRED_DATE_TIME_DESC:
                    itemSupplier = () -> findItemsWithLimit_orderByExpiredDateTimeDesc(limit);
                    break;
                case UPDATED_DATE_TIME_ASC:
                    itemSupplier = () -> findItemsWithLimit_orderByUpdatedDateTime(limit);
                    break;
                case UPDATED_DATE_TIME_DESC:
                    itemSupplier = () -> findItemsWithLimit_orderByUpdatedDateTimeDesc(limit);
                    break;
                case CREATED_DATE_TIME_ASC:
                    itemSupplier = () -> findItemsWithLimit_orderByCreatedDateTime(limit);
                    break;
                case CREATED_DATE_TIME_DESC:
                    itemSupplier = () -> findItemsWithLimit_orderByCreatedDateTimeDesc(limit);
                    break;
                default:
                    itemSupplier = () -> findItemsWithLimit(limit);
            }
        }
        return prepareItemState(itemSupplier.get());
    }

    @Query("SELECT * FROM item ORDER BY" +
            " expired_date_time DESC, updated_date_time DESC, created_date_time DESC" +
            " LIMIT :limit ")
    public abstract List<Item> findItemsWithLimit(int limit);

    @Query("SELECT * FROM item ORDER BY" +
            " expired_date_time ASC" +
            " LIMIT :limit ")
    public abstract List<Item> findItemsWithLimit_orderByExpiredDateTime(int limit);

    @Query("SELECT * FROM item ORDER BY" +
            " expired_date_time DESC" +
            " LIMIT :limit ")
    public abstract List<Item> findItemsWithLimit_orderByExpiredDateTimeDesc(int limit);

    @Query("SELECT * FROM item ORDER BY" +
            " updated_date_time ASC" +
            " LIMIT :limit ")
    public abstract List<Item> findItemsWithLimit_orderByUpdatedDateTime(int limit);

    @Query("SELECT * FROM item ORDER BY" +
            " updated_date_time DESC" +
            " LIMIT :limit ")
    public abstract List<Item> findItemsWithLimit_orderByUpdatedDateTimeDesc(int limit);

    @Query("SELECT * FROM item ORDER BY" +
            " created_date_time ASC" +
            " LIMIT :limit ")
    public abstract List<Item> findItemsWithLimit_orderByCreatedDateTime(int limit);

    @Query("SELECT * FROM item ORDER BY" +
            " created_date_time DESC" +
            " LIMIT :limit ")
    public abstract List<Item> findItemsWithLimit_orderByCreatedDateTimeDesc(int limit);

    @Insert
    protected abstract long insert(ItemImage itemImage);

    @Delete
    protected abstract void delete(ItemImage itemImage);

    @Query("SELECT * FROM item_image WHERE item_id = :itemId ORDER BY created_date_time ASC, id ASC")
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
        return findItemStatesByIds(itemId, null);
    }

    public List<ItemState> findItemStatesByIds(List<Long> itemId, QueryOrderBy queryOrderBy) {
        Supplier<List<Item>> itemSupplier;
        if (queryOrderBy == null) {
            itemSupplier = () -> findItemByIds(itemId);
        } else {
            switch (queryOrderBy) {
                case EXPIRED_DATE_TIME_ASC:
                    itemSupplier = () -> findItemByIds_orderByExpiredDateTime(itemId);
                    break;
                case EXPIRED_DATE_TIME_DESC:
                    itemSupplier = () -> findItemByIds_orderByExpiredDateTimeDesc(itemId);
                    break;
                case UPDATED_DATE_TIME_ASC:
                    itemSupplier = () -> findItemByIds_orderByUpdatedDateTime(itemId);
                    break;
                case UPDATED_DATE_TIME_DESC:
                    itemSupplier = () -> findItemByIds_orderByUpdatedDateTimeDesc(itemId);
                    break;
                case CREATED_DATE_TIME_ASC:
                    itemSupplier = () -> findItemByIds_orderByCreatedDateTime(itemId);
                    break;
                case CREATED_DATE_TIME_DESC:
                    itemSupplier = () -> findItemByIds_orderByCreatedDateTimeDesc(itemId);
                    break;
                default:
                    itemSupplier = () -> findItemByIds(itemId);
            }
        }
        return prepareItemState(itemSupplier.get());
    }

    @Query("SELECT * FROM item WHERE id IN (:ids)")
    protected abstract List<Item> findItemByIds(List<Long> ids);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY expired_date_time ASC")
    protected abstract List<Item> findItemByIds_orderByExpiredDateTime(List<Long> ids);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY expired_date_time DESC")
    protected abstract List<Item> findItemByIds_orderByExpiredDateTimeDesc(List<Long> ids);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY updated_date_time ASC")
    protected abstract List<Item> findItemByIds_orderByUpdatedDateTime(List<Long> ids);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY updated_date_time DESC")
    protected abstract List<Item> findItemByIds_orderByUpdatedDateTimeDesc(List<Long> ids);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY created_date_time ASC")
    protected abstract List<Item> findItemByIds_orderByCreatedDateTime(List<Long> ids);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY created_date_time DESC")
    protected abstract List<Item> findItemByIds_orderByCreatedDateTimeDesc(List<Long> ids);

    public List<ItemState> findItemStateByIdsWithLimit(List<Long> itemIds, QueryOrderBy queryOrderBy, int limit) {
        Supplier<List<Item>> itemSupplier;
        if (queryOrderBy == null) {
            itemSupplier = () -> findItemByIdsWithLimit(itemIds, limit);
        } else {
            switch (queryOrderBy) {
                case EXPIRED_DATE_TIME_ASC:
                    itemSupplier = () -> findItemByIdsWithLimit_orderByExpiredDateTime(itemIds, limit);
                    break;
                case EXPIRED_DATE_TIME_DESC:
                    itemSupplier = () -> findItemByIdsWithLimit_orderByExpiredDateTimeDesc(itemIds, limit);
                    break;
                case UPDATED_DATE_TIME_ASC:
                    itemSupplier = () -> findItemByIdsWithLimit_orderByUpdatedDateTime(itemIds, limit);
                    break;
                case UPDATED_DATE_TIME_DESC:
                    itemSupplier = () -> findItemByIdsWithLimit_orderByUpdatedDateTimeDesc(itemIds, limit);
                    break;
                case CREATED_DATE_TIME_ASC:
                    itemSupplier = () -> findItemByIdsWithLimit_orderByCreatedDateTime(itemIds, limit);
                    break;
                case CREATED_DATE_TIME_DESC:
                    itemSupplier = () -> findItemByIdsWithLimit_orderByCreatedDateTimeDesc(itemIds, limit);
                    break;
                default:
                    itemSupplier = () -> findItemByIdsWithLimit(itemIds, limit);
            }
        }
        return prepareItemState(itemSupplier.get());
    }

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY" +
            " expired_date_time DESC, updated_date_time DESC, created_date_time DESC" +
            " LIMIT :limit")
    protected abstract List<Item> findItemByIdsWithLimit(List<Long> ids, int limit);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY expired_date_time ASC LIMIT :limit")
    protected abstract List<Item> findItemByIdsWithLimit_orderByExpiredDateTime(List<Long> ids, int limit);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY expired_date_time DESC LIMIT :limit")
    protected abstract List<Item> findItemByIdsWithLimit_orderByExpiredDateTimeDesc(List<Long> ids, int limit);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY updated_date_time ASC LIMIT :limit")
    protected abstract List<Item> findItemByIdsWithLimit_orderByUpdatedDateTime(List<Long> ids, int limit);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY updated_date_time DESC LIMIT :limit")
    protected abstract List<Item> findItemByIdsWithLimit_orderByUpdatedDateTimeDesc(List<Long> ids, int limit);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY created_date_time ASC LIMIT :limit")
    protected abstract List<Item> findItemByIdsWithLimit_orderByCreatedDateTime(List<Long> ids, int limit);

    @Query("SELECT * FROM item WHERE id IN (:ids) ORDER BY created_date_time DESC LIMIT :limit")
    protected abstract List<Item> findItemByIdsWithLimit_orderByCreatedDateTimeDesc(List<Long> ids, int limit);

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
    public abstract Item findItemById(long itemId);

    @Query("SELECT * FROM item")
    public abstract List<Item> findAllItems();

    @Query("SELECT COUNT(*) FROM item")
    public abstract int countItems();

    @Query("SELECT SUM(price * amount) FROM item")
    public abstract Double sumInventoryValue();

    @TypeConverters({Converter.class})
    @Query("SELECT * FROM item WHERE expired_date_time IS NOT NULL" +
            " AND expired_date_time <= :to" +
            " ORDER BY expired_date_time ASC")
    public abstract List<Item> findItemsExpiringBefore(Date to);

    @TypeConverters({Converter.class})
    @Query("SELECT COUNT(*) FROM item WHERE expired_date_time IS NOT NULL" +
            " AND expired_date_time <= :to")
    public abstract int countItemsExpiringBefore(Date to);

    @Query("SELECT * FROM item WHERE min_amount IS NOT NULL" +
            " AND min_amount > 0")
    public abstract List<Item> findItemsWithMinAmount();
}
