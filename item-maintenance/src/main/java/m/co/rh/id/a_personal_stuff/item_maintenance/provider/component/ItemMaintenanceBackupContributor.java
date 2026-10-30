package m.co.rh.id.a_personal_stuff.item_maintenance.provider.component;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import m.co.rh.id.a_personal_stuff.base.backup.BackupFileUtils;
import m.co.rh.id.a_personal_stuff.base.backup.ExportImageDir;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleBackupContributor;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleExport;
import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceFileHelper;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.notifier.ItemMaintenanceChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the item-maintenance module's backup/export/import behavior
 * (backup.json sections, image dirs, import with id remap and notifier
 * events). JSON field names and top-level keys are byte-compatible with the
 * pre-contract BackupData format.
 */
public class ItemMaintenanceBackupContributor implements ModuleBackupContributor {

    private static final String TAG = ItemMaintenanceBackupContributor.class.getName();
    private static final String KEY_ITEM_MAINTENANCES = "itemMaintenances";
    private static final String KEY_ITEM_MAINTENANCE_IMAGES = "itemMaintenanceImages";

    private final ILogger mLogger;
    private final FileHelper mFileHelper;
    private final ItemMaintenanceDao mItemMaintenanceDao;
    private final ItemMaintenanceFileHelper mItemMaintenanceFileHelper;
    private final ItemMaintenanceChangeNotifier mItemMaintenanceChangeNotifier;

    public ItemMaintenanceBackupContributor(Provider provider) {
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mItemMaintenanceFileHelper = provider.get(ItemMaintenanceFileHelper.class);
        mItemMaintenanceChangeNotifier = provider.get(ItemMaintenanceChangeNotifier.class);
    }

    @Override
    public List<String> getJsonKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(KEY_ITEM_MAINTENANCES);
        keys.add(KEY_ITEM_MAINTENANCE_IMAGES);
        return keys;
    }

    @Override
    public ModuleExport exportData() {
        List<ItemMaintenance> itemMaintenances = mItemMaintenanceDao.findAllItemMaintenances();
        List<ItemMaintenanceImage> itemMaintenanceImages = mItemMaintenanceDao.findAllItemMaintenanceImages();
        try {
            List<JSONObject> sections = new ArrayList<>();
            sections.add(maintenancesSection(itemMaintenances));
            sections.add(maintenanceImagesSection(itemMaintenanceImages));

            List<ExportImageDir> imageDirs = new ArrayList<>();
            imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE,
                    mItemMaintenanceFileHelper.getItemMaintenanceImageParent(),
                    referencedFileNames(itemMaintenanceImages)));
            imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL,
                    mItemMaintenanceFileHelper.getItemMaintenanceImageThumbnailParent(),
                    referencedFileNames(itemMaintenanceImages)));
            return ModuleExport.ofSectionsAndImageDirs(sections, imageDirs);
        } catch (JSONException e) {
            // cannot happen: only primitive values are written
            throw new IllegalStateException("Item maintenance backup serialization failed", e);
        }
    }

    @Override
    public void importJson(JSONObject root, Map<Long, Long> oldToNewItemId, File tempDir) throws JSONException {
        List<ItemMaintenance> itemMaintenances = maintenancesFromJson(root.optJSONArray(KEY_ITEM_MAINTENANCES));
        List<ItemMaintenanceImage> itemMaintenanceImages = maintenanceImagesFromJson(root.optJSONArray(KEY_ITEM_MAINTENANCE_IMAGES));
        Map<Long, List<ItemMaintenanceImage>> maintImagesByOldMaintId = groupMaintImagesByMaintId(itemMaintenanceImages);
        for (ItemMaintenance e : itemMaintenances) {
            Long newItemId = oldToNewItemId.get(e.itemId);
            if (newItemId == null) continue;
            long oldId = e.id;
            e.id = null;
            e.itemId = newItemId;
            long newId = mItemMaintenanceDao.insert(e);
            e.id = newId;
            List<ItemMaintenanceImage> relatedImages = maintImagesByOldMaintId.getOrDefault(oldId, new ArrayList<>());
            ArrayList<ItemMaintenanceImage> maintImages = new ArrayList<>();
            for (ItemMaintenanceImage ie : relatedImages) {
                ie.id = null;
                ie.itemMaintenanceId = newId;
                String oldFileName = null;
                String newFileName = null;
                if (ie.fileName != null && !ie.fileName.isEmpty()) {
                    oldFileName = ie.fileName;
                    newFileName = mFileHelper.generateImageFileName();
                    ie.fileName = newFileName;
                }
                // insert row before copying image files so file helper cleanUp passes never see an orphan file
                ie.id = mItemMaintenanceDao.insert(ie);
                if (oldFileName != null) {
                    BackupFileUtils.copyImageFromTemp(mLogger, TAG, tempDir, Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE, oldFileName, newFileName, mItemMaintenanceFileHelper.getItemMaintenanceImageParent());
                    BackupFileUtils.copyImageFromTemp(mLogger, TAG, tempDir, Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL, oldFileName, newFileName, mItemMaintenanceFileHelper.getItemMaintenanceImageThumbnailParent());
                }
                maintImages.add(ie);
            }
            ItemMaintenanceState maintState = new ItemMaintenanceState();
            maintState.updateItemMaintenance(e);
            if (!maintImages.isEmpty()) {
                maintState.updateItemMaintenanceImages(maintImages);
            }
            mItemMaintenanceChangeNotifier.itemMaintenanceAdded(maintState.clone());
        }
    }

    private static Set<String> referencedFileNames(List<ItemMaintenanceImage> images) {
        Set<String> fileNames = new HashSet<>();
        for (ItemMaintenanceImage image : images) {
            if (image.fileName != null) fileNames.add(image.fileName);
        }
        return fileNames;
    }

    private static Map<Long, List<ItemMaintenanceImage>> groupMaintImagesByMaintId(List<ItemMaintenanceImage> images) {
        Map<Long, List<ItemMaintenanceImage>> map = new HashMap<>();
        for (ItemMaintenanceImage img : images) {
            map.computeIfAbsent(img.itemMaintenanceId, k -> new ArrayList<>()).add(img);
        }
        return map;
    }

    // Serialization helpers are public static so tests can assemble fixture
    // backups with the exact production field names.

    public static JSONObject maintenancesSection(List<ItemMaintenance> entries) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ITEM_MAINTENANCES, maintenancesToJson(entries));
        return obj;
    }

    public static JSONObject maintenanceImagesSection(List<ItemMaintenanceImage> entries) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ITEM_MAINTENANCE_IMAGES, maintenanceImagesToJson(entries));
        return obj;
    }

    public static JSONArray maintenancesToJson(List<ItemMaintenance> entries) throws JSONException {
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

    public static List<ItemMaintenance> maintenancesFromJson(JSONArray arr) throws JSONException {
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

    public static JSONArray maintenanceImagesToJson(List<ItemMaintenanceImage> entries) throws JSONException {
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

    public static List<ItemMaintenanceImage> maintenanceImagesFromJson(JSONArray arr) throws JSONException {
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
}
