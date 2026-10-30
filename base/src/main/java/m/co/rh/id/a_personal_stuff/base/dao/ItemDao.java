package m.co.rh.id.a_personal_stuff.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Transaction;
import androidx.room.TypeConverters;
import androidx.room.Update;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public static final int SQLITE_IN_CHUNK_SIZE = 500;

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

    public static List<List<Long>> chunkIds(List<Long> ids) {
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
        String sql = "SELECT * FROM item" + orderClause(queryOrderBy) + " LIMIT ?";
        return prepareItemState(findItemsRaw(new SimpleSQLiteQuery(sql, new Object[]{limit})));
    }

    @RawQuery
    protected abstract List<Item> findItemsRaw(SupportSQLiteQuery query);

    /**
     * ORDER BY fragment per {@link QueryOrderBy}, identical to the former
     * hand-written @Query sort matrix: the null/default composite is
     * expired DESC, updated DESC, created DESC and the single-key variants
     * have no tiebreaker. The fragment comes ONLY from this enum - never
     * user input - which is the injection boundary that makes the string
     * concatenation in the @RawQuery wrappers safe.
     */
    private static String orderClause(QueryOrderBy queryOrderBy) {
        if (queryOrderBy == null) {
            return " ORDER BY expired_date_time DESC, updated_date_time DESC, created_date_time DESC";
        }
        switch (queryOrderBy) {
            case EXPIRED_DATE_TIME_ASC:
                return " ORDER BY expired_date_time ASC";
            case EXPIRED_DATE_TIME_DESC:
                return " ORDER BY expired_date_time DESC";
            case UPDATED_DATE_TIME_ASC:
                return " ORDER BY updated_date_time ASC";
            case UPDATED_DATE_TIME_DESC:
                return " ORDER BY updated_date_time DESC";
            case CREATED_DATE_TIME_ASC:
                return " ORDER BY created_date_time ASC";
            case CREATED_DATE_TIME_DESC:
                return " ORDER BY created_date_time DESC";
            default:
                return " ORDER BY expired_date_time DESC, updated_date_time DESC, created_date_time DESC";
        }
    }

    private static String placeholders(int count) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                stringBuilder.append(',');
            }
            stringBuilder.append('?');
        }
        return stringBuilder.toString();
    }

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
        String sql = "SELECT * FROM item WHERE id IN (" + placeholders(itemId.size()) + ")"
                + orderClause(queryOrderBy);
        return prepareItemState(findItemsRaw(new SimpleSQLiteQuery(sql, itemId.toArray())));
    }

    public List<ItemState> findItemStateByIdsWithLimit(List<Long> itemIds, QueryOrderBy queryOrderBy, int limit) {
        String sql = "SELECT * FROM item WHERE id IN (" + placeholders(itemIds.size()) + ")"
                + orderClause(queryOrderBy) + " LIMIT ?";
        List<Object> args = new ArrayList<>(itemIds);
        args.add(limit);
        return prepareItemState(findItemsRaw(new SimpleSQLiteQuery(sql, args.toArray())));
    }

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

    @Query("SELECT id FROM item")
    public abstract List<Long> getAllItemIds();

    @Query("SELECT COUNT(*) FROM item")
    public abstract int countItems();

    @Query("SELECT SUM(price * amount) FROM item")
    public abstract Double sumInventoryValue();

    @TypeConverters({Converter.class})
    @Query("SELECT * FROM item WHERE expired_date_time IS NOT NULL" +
            " AND expired_date_time <= :to" +
            " ORDER BY expired_date_time ASC, id ASC")
    public abstract List<Item> findItemsExpiringBefore(Date to);

    @TypeConverters({Converter.class})
    @Query("SELECT COUNT(*) FROM item WHERE expired_date_time IS NOT NULL" +
            " AND expired_date_time <= :to")
    public abstract int countItemsExpiringBefore(Date to);

    @Query("SELECT * FROM item WHERE min_amount IS NOT NULL" +
            " AND min_amount > 0" +
            " ORDER BY id ASC")
    public abstract List<Item> findItemsWithMinAmount();
}
