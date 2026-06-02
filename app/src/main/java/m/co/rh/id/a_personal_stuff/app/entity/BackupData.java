package m.co.rh.id.a_personal_stuff.app.entity;

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BackupData {
    private static final String KEY_VERSION = "version";
    private static final String KEY_EXPORTED_AT = "exportedAt";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_ITEM_IMAGES = "itemImages";
    private static final String KEY_ITEM_TAGS = "itemTags";
    private static final String KEY_ITEM_MAINTENANCES = "itemMaintenances";
    private static final String KEY_ITEM_MAINTENANCE_IMAGES = "itemMaintenanceImages";
    private static final String KEY_ITEM_USAGES = "itemUsages";
    private static final String KEY_ITEM_USAGE_IMAGES = "itemUsageImages";
    private static final String KEY_ITEM_REMINDERS = "itemReminders";

    private static final int CURRENT_VERSION = 1;

    public int version;
    public long exportedAt;
    public List<Item> items;
    public List<ItemImage> itemImages;
    public List<ItemTag> itemTags;
    public List<ItemMaintenance> itemMaintenances;
    public List<ItemMaintenanceImage> itemMaintenanceImages;
    public List<ItemUsage> itemUsages;
    public List<ItemUsageImage> itemUsageImages;
    public List<ItemReminder> itemReminders;

    public BackupData() {
        version = CURRENT_VERSION;
        exportedAt = new Date().getTime();
        items = new ArrayList<>();
        itemImages = new ArrayList<>();
        itemTags = new ArrayList<>();
        itemMaintenances = new ArrayList<>();
        itemMaintenanceImages = new ArrayList<>();
        itemUsages = new ArrayList<>();
        itemUsageImages = new ArrayList<>();
        itemReminders = new ArrayList<>();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_VERSION, version);
        json.put(KEY_EXPORTED_AT, exportedAt);
        json.put(KEY_ITEMS, itemListToJson(items));
        json.put(KEY_ITEM_IMAGES, imageListToJson(itemImages));
        json.put(KEY_ITEM_TAGS, tagListToJson(itemTags));
        json.put(KEY_ITEM_MAINTENANCES, maintenanceListToJson(itemMaintenances));
        json.put(KEY_ITEM_MAINTENANCE_IMAGES, maintenanceImageListToJson(itemMaintenanceImages));
        json.put(KEY_ITEM_USAGES, usageListToJson(itemUsages));
        json.put(KEY_ITEM_USAGE_IMAGES, usageImageListToJson(itemUsageImages));
        json.put(KEY_ITEM_REMINDERS, reminderListToJson(itemReminders));
        return json;
    }

    public static BackupData fromJson(JSONObject json) throws JSONException {
        BackupData data = new BackupData();
        data.version = json.optInt(KEY_VERSION, 1);
        data.exportedAt = json.optLong(KEY_EXPORTED_AT, 0);
        data.items = jsonToItemList(json.optJSONArray(KEY_ITEMS));
        data.itemImages = jsonToImageList(json.optJSONArray(KEY_ITEM_IMAGES));
        data.itemTags = jsonToTagList(json.optJSONArray(KEY_ITEM_TAGS));
        data.itemMaintenances = jsonToMaintenanceList(json.optJSONArray(KEY_ITEM_MAINTENANCES));
        data.itemMaintenanceImages = jsonToMaintenanceImageList(json.optJSONArray(KEY_ITEM_MAINTENANCE_IMAGES));
        data.itemUsages = jsonToUsageList(json.optJSONArray(KEY_ITEM_USAGES));
        data.itemUsageImages = jsonToUsageImageList(json.optJSONArray(KEY_ITEM_USAGE_IMAGES));
        data.itemReminders = jsonToReminderList(json.optJSONArray(KEY_ITEM_REMINDERS));
        return data;
    }

    private static JSONArray itemListToJson(List<Item> items) throws JSONException {
        JSONArray arr = new JSONArray();
        for (Item item : items) {
            JSONObject obj = new JSONObject();
            obj.put("id", item.id);
            obj.put("name", item.name);
            obj.put("amount", item.amount);
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

    private static JSONArray maintenanceListToJson(List<ItemMaintenance> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemMaintenance e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemId", e.itemId);
            obj.put("description", e.description);
            obj.put("cost", e.cost != null ? e.cost.toString() : JSONObject.NULL);
            obj.put("maintenanceDateTime", e.maintenanceDateTime != null ? e.maintenanceDateTime.getTime() : JSONObject.NULL);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    private static List<ItemMaintenance> jsonToMaintenanceList(JSONArray arr) throws JSONException {
        List<ItemMaintenance> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemMaintenance e = new ItemMaintenance();
            e.id = obj.optLong("id", 0);
            e.itemId = obj.optLong("itemId", 0);
            e.description = obj.optString("description", null);
            String costStr = obj.isNull("cost") ? null : obj.optString("cost", null);
            e.cost = costStr != null && !costStr.isEmpty() ? new BigDecimal(costStr) : null;
            e.maintenanceDateTime = obj.isNull("maintenanceDateTime") ? null : new Date(obj.getLong("maintenanceDateTime"));
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(e);
        }
        return list;
    }

    private static JSONArray maintenanceImageListToJson(List<ItemMaintenanceImage> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemMaintenanceImage e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemMaintenanceId", e.itemMaintenanceId);
            obj.put("fileName", e.fileName);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    private static List<ItemMaintenanceImage> jsonToMaintenanceImageList(JSONArray arr) throws JSONException {
        List<ItemMaintenanceImage> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemMaintenanceImage e = new ItemMaintenanceImage();
            e.id = obj.optLong("id", 0);
            e.itemMaintenanceId = obj.optLong("itemMaintenanceId", 0);
            e.fileName = obj.optString("fileName", null);
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(e);
        }
        return list;
    }

    private static JSONArray usageListToJson(List<ItemUsage> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemUsage e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemId", e.itemId);
            obj.put("description", e.description);
            obj.put("amount", e.amount);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    private static List<ItemUsage> jsonToUsageList(JSONArray arr) throws JSONException {
        List<ItemUsage> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemUsage e = new ItemUsage();
            e.id = obj.optLong("id", 0);
            e.itemId = obj.optLong("itemId", 0);
            e.description = obj.optString("description", null);
            e.amount = obj.optInt("amount", 0);
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(e);
        }
        return list;
    }

    private static JSONArray usageImageListToJson(List<ItemUsageImage> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemUsageImage e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemUsageId", e.itemUsageId);
            obj.put("fileName", e.fileName);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    private static List<ItemUsageImage> jsonToUsageImageList(JSONArray arr) throws JSONException {
        List<ItemUsageImage> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemUsageImage e = new ItemUsageImage();
            e.id = obj.optLong("id", 0);
            e.itemUsageId = obj.optLong("itemUsageId", 0);
            e.fileName = obj.optString("fileName", null);
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(e);
        }
        return list;
    }

    private static JSONArray reminderListToJson(List<ItemReminder> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemReminder e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemId", e.itemId);
            obj.put("taskId", e.taskId);
            obj.put("reminderDateTime", e.reminderDateTime != null ? e.reminderDateTime.getTime() : JSONObject.NULL);
            obj.put("message", e.message);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    private static List<ItemReminder> jsonToReminderList(JSONArray arr) throws JSONException {
        List<ItemReminder> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemReminder e = new ItemReminder();
            e.id = obj.optLong("id", 0);
            e.itemId = obj.optLong("itemId", 0);
            e.taskId = obj.optString("taskId", null);
            e.reminderDateTime = obj.isNull("reminderDateTime") ? null : new Date(obj.getLong("reminderDateTime"));
            e.message = obj.optString("message", null);
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(e);
        }
        return list;
    }
}