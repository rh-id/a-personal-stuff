package m.co.rh.id.a_personal_stuff.item_checklist.provider.component;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_personal_stuff.base.backup.ModuleBackupContributor;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleExport;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the item-checklist module's backup/export/import behavior.
 * Checklists have no image files. The import owns BOTH its sections' id
 * remaps: checklists are inserted first (keeping their created/updated
 * timestamps), then checklist items are remapped through the new checklist
 * ids AND the caller's old→new item id map — rows whose checklist or item no
 * longer resolves are skipped.
 */
public class ItemChecklistBackupContributor implements ModuleBackupContributor {

    private static final String KEY_ITEM_CHECKLISTS = "itemChecklists";
    private static final String KEY_ITEM_CHECKLIST_ITEMS = "itemChecklistItems";

    private final ItemChecklistDao mItemChecklistDao;
    private final ItemChecklistChangeNotifier mItemChecklistChangeNotifier;

    public ItemChecklistBackupContributor(Provider provider) {
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
        mItemChecklistChangeNotifier = provider.get(ItemChecklistChangeNotifier.class);
    }

    @Override
    public List<String> getJsonKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(KEY_ITEM_CHECKLISTS);
        keys.add(KEY_ITEM_CHECKLIST_ITEMS);
        return keys;
    }

    @Override
    public ModuleExport exportData() {
        List<ItemChecklist> itemChecklists = mItemChecklistDao.findAllItemChecklists();
        List<ItemChecklistItem> itemChecklistItems = mItemChecklistDao.findAllItemChecklistItems();
        try {
            List<JSONObject> sections = new ArrayList<>();
            sections.add(checklistsSection(itemChecklists));
            sections.add(checklistItemsSection(itemChecklistItems));
            return ModuleExport.ofSections(sections.toArray(new JSONObject[0]));
        } catch (JSONException e) {
            // cannot happen: only primitive values are written
            throw new IllegalStateException("Item checklist backup serialization failed", e);
        }
    }

    @Override
    public void importJson(JSONObject root, Map<Long, Long> oldToNewItemId, File tempDir) throws JSONException {
        List<ItemChecklist> itemChecklists = checklistsFromJson(root.optJSONArray(KEY_ITEM_CHECKLISTS));
        List<ItemChecklistItem> itemChecklistItems = checklistItemsFromJson(root.optJSONArray(KEY_ITEM_CHECKLIST_ITEMS));
        Map<Long, Long> oldToNewItemChecklistId = new HashMap<>();
        for (ItemChecklist e : itemChecklists) {
            long oldId = e.id;
            // Preserve original createdDateTime and updatedDateTime
            Date originalCreatedDateTime = e.createdDateTime;
            Date originalUpdatedDateTime = e.updatedDateTime;
            e.id = null;
            ItemChecklistState checklistState = new ItemChecklistState();
            checklistState.updateItemChecklist(e);
            // Set the dates explicitly to preserve them
            if (originalCreatedDateTime != null) {
                checklistState.setCreatedDateTime(originalCreatedDateTime);
            }
            if (originalUpdatedDateTime != null) {
                checklistState.setUpdatedDateTime(originalUpdatedDateTime);
            }
            mItemChecklistDao.insertItemChecklist(checklistState);
            long newId = checklistState.getChecklistId();
            oldToNewItemChecklistId.put(oldId, newId);
            mItemChecklistChangeNotifier.checklistAdded(checklistState.clone());
        }
        for (ItemChecklistItem e : itemChecklistItems) {
            Long newItemChecklistId = oldToNewItemChecklistId.get(e.itemChecklistId);
            Long newItemId = oldToNewItemId.get(e.itemId);
            if (newItemChecklistId == null || newItemId == null) continue;
            e.id = null;
            e.itemChecklistId = newItemChecklistId;
            e.itemId = newItemId;
            // Preserve checkedDateTime and createdDateTime
            List<Long> inserted = mItemChecklistDao.insertItemChecklistItems(
                    Collections.singletonList(e));
            if (!inserted.isEmpty() && inserted.get(0) != null && inserted.get(0) != -1) {
                e.id = inserted.get(0);
                mItemChecklistChangeNotifier.checklistItemAdded(e, null);
            }
        }
    }

    // Serialization helpers are public static so tests can assemble fixture
    // backups with the exact production field names.

    public static JSONObject checklistsSection(List<ItemChecklist> entries) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ITEM_CHECKLISTS, checklistsToJson(entries));
        return obj;
    }

    public static JSONObject checklistItemsSection(List<ItemChecklistItem> entries) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ITEM_CHECKLIST_ITEMS, checklistItemsToJson(entries));
        return obj;
    }

    public static JSONArray checklistsToJson(List<ItemChecklist> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemChecklist e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("title", e.title);
            obj.put("description", e.description);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            obj.put("updatedDateTime", e.updatedDateTime != null ? e.updatedDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    public static List<ItemChecklist> checklistsFromJson(JSONArray arr) throws JSONException {
        List<ItemChecklist> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemChecklist e = new ItemChecklist();
            e.id = obj.optLong("id", 0);
            e.title = obj.optString("title", null);
            e.description = obj.optString("description", null);
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            e.updatedDateTime = obj.isNull("updatedDateTime") ? null : new Date(obj.getLong("updatedDateTime"));
            list.add(e);
        }
        return list;
    }

    public static JSONArray checklistItemsToJson(List<ItemChecklistItem> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemChecklistItem e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemChecklistId", e.itemChecklistId);
            obj.put("itemId", e.itemId);
            obj.put("checkedDateTime", e.checkedDateTime != null ? e.checkedDateTime.getTime() : JSONObject.NULL);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    public static List<ItemChecklistItem> checklistItemsFromJson(JSONArray arr) throws JSONException {
        List<ItemChecklistItem> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemChecklistItem e = new ItemChecklistItem();
            e.id = obj.optLong("id", 0);
            e.itemChecklistId = obj.optLong("itemChecklistId", 0);
            e.itemId = obj.optLong("itemId", 0);
            e.checkedDateTime = obj.isNull("checkedDateTime") ? null : new Date(obj.getLong("checkedDateTime"));
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(e);
        }
        return list;
    }
}
