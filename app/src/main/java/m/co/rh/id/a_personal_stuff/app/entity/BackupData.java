package m.co.rh.id.a_personal_stuff.app.entity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;

/**
 * The base-DB core of a backup: metadata plus the items anchor (items,
 * item images, item tags). :app owns this section because the items table is
 * the import anchor — it is imported first and supplies the old→new item id
 * remap every module contributor needs. Each feature module's sections are
 * written and parsed by its own
 * {@link m.co.rh.id.a_personal_stuff.base.backup.ModuleBackupContributor};
 * this class deliberately knows nothing about them.
 */
public class BackupData {
    private static final String KEY_VERSION = "version";
    private static final String KEY_EXPORTED_AT = "exportedAt";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_ITEM_IMAGES = "itemImages";
    private static final String KEY_ITEM_TAGS = "itemTags";

    private static final int CURRENT_VERSION = 2;

    public int version;
    public long exportedAt;
    public List<Item> items;
    public List<ItemImage> itemImages;
    public List<ItemTag> itemTags;

    public BackupData() {
        version = CURRENT_VERSION;
        exportedAt = new Date().getTime();
        items = new ArrayList<>();
        itemImages = new ArrayList<>();
        itemTags = new ArrayList<>();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_VERSION, version);
        json.put(KEY_EXPORTED_AT, exportedAt);
        json.put(KEY_ITEMS, itemListToJson(items));
        json.put(KEY_ITEM_IMAGES, imageListToJson(itemImages));
        json.put(KEY_ITEM_TAGS, tagListToJson(itemTags));
        return json;
    }

    public static BackupData fromJson(JSONObject json) throws JSONException {
        BackupData data = new BackupData();
        data.version = json.optInt(KEY_VERSION, 1);
        data.exportedAt = json.optLong(KEY_EXPORTED_AT, 0);
        data.items = jsonToItemList(json.optJSONArray(KEY_ITEMS));
        data.itemImages = jsonToImageList(json.optJSONArray(KEY_ITEM_IMAGES));
        data.itemTags = jsonToTagList(json.optJSONArray(KEY_ITEM_TAGS));
        return data;
    }

    private static JSONArray itemListToJson(List<Item> items) throws JSONException {
        JSONArray arr = new JSONArray();
        for (Item item : items) {
            JSONObject obj = new JSONObject();
            obj.put("id", item.id);
            obj.put("name", item.name);
            obj.put("amount", item.amount);
            obj.put("minAmount", item.minAmount != null ? item.minAmount : JSONObject.NULL);
            obj.put("price", item.price != null ? item.price.toString() : JSONObject.NULL);
            obj.put("description", item.description);
            obj.put("barcode", item.barcode);
            obj.put("expiredDateTime", item.expiredDateTime != null ? item.expiredDateTime.getTime() : JSONObject.NULL);
            obj.put("createdDateTime", item.createdDateTime != null ? item.createdDateTime.getTime() : JSONObject.NULL);
            obj.put("updatedDateTime", item.updatedDateTime != null ? item.updatedDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    private static List<Item> jsonToItemList(JSONArray arr) throws JSONException {
        List<Item> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            Item item = new Item();
            item.id = obj.optLong("id", 0);
            item.name = obj.optString("name", null);
            item.amount = obj.optInt("amount", 0);
            item.minAmount = obj.isNull("minAmount") ? null : obj.optInt("minAmount");
            String priceStr = obj.isNull("price") ? null : obj.optString("price", null);
            item.price = priceStr != null && !priceStr.isEmpty() ? new BigDecimal(priceStr) : null;
            item.description = obj.optString("description", null);
            item.barcode = obj.optString("barcode", null);
            item.expiredDateTime = obj.isNull("expiredDateTime") ? null : new Date(obj.getLong("expiredDateTime"));
            item.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            item.updatedDateTime = obj.isNull("updatedDateTime") ? null : new Date(obj.getLong("updatedDateTime"));
            list.add(item);
        }
        return list;
    }

    private static JSONArray imageListToJson(List<ItemImage> images) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemImage img : images) {
            JSONObject obj = new JSONObject();
            obj.put("id", img.id);
            obj.put("itemId", img.itemId);
            obj.put("fileName", img.fileName);
            obj.put("createdDateTime", img.createdDateTime != null ? img.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    private static List<ItemImage> jsonToImageList(JSONArray arr) throws JSONException {
        List<ItemImage> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemImage img = new ItemImage();
            img.id = obj.optLong("id", 0);
            img.itemId = obj.optLong("itemId", 0);
            img.fileName = obj.optString("fileName", null);
            img.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(img);
        }
        return list;
    }

    private static JSONArray tagListToJson(List<ItemTag> tags) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemTag tag : tags) {
            JSONObject obj = new JSONObject();
            obj.put("id", tag.id);
            obj.put("itemId", tag.itemId);
            obj.put("tag", tag.tag);
            obj.put("createdDateTime", tag.createdDateTime != null ? tag.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    private static List<ItemTag> jsonToTagList(JSONArray arr) throws JSONException {
        List<ItemTag> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemTag tag = new ItemTag();
            tag.id = obj.optLong("id", 0);
            tag.itemId = obj.optLong("itemId", 0);
            tag.tag = obj.optString("tag", null);
            tag.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(tag);
        }
        return list;
    }
}
