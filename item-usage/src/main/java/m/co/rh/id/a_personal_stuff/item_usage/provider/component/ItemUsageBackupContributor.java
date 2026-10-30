package m.co.rh.id.a_personal_stuff.item_usage.provider.component;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
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
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the item-usage module's backup/export/import behavior
 * (backup.json sections, image dirs, import with id remap and notifier
 * events). JSON field names and top-level keys are byte-compatible with the
 * pre-contract BackupData format.
 */
public class ItemUsageBackupContributor implements ModuleBackupContributor {

    private static final String TAG = ItemUsageBackupContributor.class.getName();
    private static final String KEY_ITEM_USAGES = "itemUsages";
    private static final String KEY_ITEM_USAGE_IMAGES = "itemUsageImages";

    private final ILogger mLogger;
    private final FileHelper mFileHelper;
    private final ItemUsageDao mItemUsageDao;
    private final ItemUsageFileHelper mItemUsageFileHelper;
    private final ItemUsageChangeNotifier mItemUsageChangeNotifier;

    public ItemUsageBackupContributor(Provider provider) {
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mItemUsageFileHelper = provider.get(ItemUsageFileHelper.class);
        mItemUsageChangeNotifier = provider.get(ItemUsageChangeNotifier.class);
    }

    @Override
    public List<String> getJsonKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(KEY_ITEM_USAGES);
        keys.add(KEY_ITEM_USAGE_IMAGES);
        return keys;
    }

    @Override
    public ModuleExport exportData() {
        List<ItemUsage> itemUsages = mItemUsageDao.findAllItemUsages();
        List<ItemUsageImage> itemUsageImages = mItemUsageDao.findAllItemUsageImages();
        try {
            List<JSONObject> sections = new ArrayList<>();
            sections.add(usagesSection(itemUsages));
            sections.add(usageImagesSection(itemUsageImages));

            List<ExportImageDir> imageDirs = new ArrayList<>();
            imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_USAGE_IMAGE,
                    mItemUsageFileHelper.getItemUsageImageParent(),
                    referencedFileNames(itemUsageImages)));
            imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL,
                    mItemUsageFileHelper.getItemUsageImageThumbnailParent(),
                    referencedFileNames(itemUsageImages)));
            return ModuleExport.ofSectionsAndImageDirs(sections, imageDirs);
        } catch (JSONException e) {
            // cannot happen: only primitive values are written
            throw new IllegalStateException("Item usage backup serialization failed", e);
        }
    }

    @Override
    public void importJson(JSONObject root, Map<Long, Long> oldToNewItemId, File tempDir) throws JSONException {
        List<ItemUsage> itemUsages = usagesFromJson(root.optJSONArray(KEY_ITEM_USAGES));
        List<ItemUsageImage> itemUsageImages = usageImagesFromJson(root.optJSONArray(KEY_ITEM_USAGE_IMAGES));
        Map<Long, List<ItemUsageImage>> usageImagesByOldUsageId = groupUsageImagesByUsageId(itemUsageImages);
        for (ItemUsage e : itemUsages) {
            Long newItemId = oldToNewItemId.get(e.itemId);
            if (newItemId == null) continue;
            long oldId = e.id;
            e.id = null;
            e.itemId = newItemId;
            // Handle null usageDateTime from old backups
            if (e.usageDateTime == null) {
                e.usageDateTime = e.createdDateTime;
            }
            long newId = mItemUsageDao.insert(e);
            e.id = newId;
            List<ItemUsageImage> relatedImages = usageImagesByOldUsageId.getOrDefault(oldId, new ArrayList<>());
            ArrayList<ItemUsageImage> usageImages = new ArrayList<>();
            for (ItemUsageImage ie : relatedImages) {
                ie.id = null;
                ie.itemUsageId = newId;
                String oldFileName = null;
                String newFileName = null;
                if (ie.fileName != null && !ie.fileName.isEmpty()) {
                    oldFileName = ie.fileName;
                    newFileName = mFileHelper.generateImageFileName();
                    ie.fileName = newFileName;
                }
                // insert row before copying image files so file helper cleanUp passes never see an orphan file
                ie.id = mItemUsageDao.insert(ie);
                if (oldFileName != null) {
                    BackupFileUtils.copyImageFromTemp(mLogger, TAG, tempDir, Constants.FILE_DIR_ITEM_USAGE_IMAGE, oldFileName, newFileName, mItemUsageFileHelper.getItemUsageImageParent());
                    BackupFileUtils.copyImageFromTemp(mLogger, TAG, tempDir, Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL, oldFileName, newFileName, mItemUsageFileHelper.getItemUsageImageThumbnailParent());
                }
                usageImages.add(ie);
            }
            ItemUsageState usageState = new ItemUsageState();
            usageState.updateItemUsage(e);
            if (!usageImages.isEmpty()) {
                usageState.updateItemUsageImages(usageImages);
            }
            mItemUsageChangeNotifier.itemUsageAdded(usageState.clone());
        }
    }

    private static Set<String> referencedFileNames(List<ItemUsageImage> images) {
        Set<String> fileNames = new HashSet<>();
        for (ItemUsageImage image : images) {
            if (image.fileName != null) fileNames.add(image.fileName);
        }
        return fileNames;
    }

    private static Map<Long, List<ItemUsageImage>> groupUsageImagesByUsageId(List<ItemUsageImage> images) {
        Map<Long, List<ItemUsageImage>> map = new HashMap<>();
        for (ItemUsageImage img : images) {
            map.computeIfAbsent(img.itemUsageId, k -> new ArrayList<>()).add(img);
        }
        return map;
    }

    // Serialization helpers are public static so tests can assemble fixture
    // backups with the exact production field names.

    public static JSONObject usagesSection(List<ItemUsage> entries) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ITEM_USAGES, usagesToJson(entries));
        return obj;
    }

    public static JSONObject usageImagesSection(List<ItemUsageImage> entries) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ITEM_USAGE_IMAGES, usageImagesToJson(entries));
        return obj;
    }

    public static JSONArray usagesToJson(List<ItemUsage> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemUsage e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemId", e.itemId);
            obj.put("description", e.description);
            obj.put("amount", e.amount);
            obj.put("usageDateTime", e.usageDateTime != null ? e.usageDateTime.getTime() : JSONObject.NULL);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    public static List<ItemUsage> usagesFromJson(JSONArray arr) throws JSONException {
        List<ItemUsage> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemUsage e = new ItemUsage();
            e.id = obj.optLong("id", 0);
            e.itemId = obj.optLong("itemId", 0);
            e.description = obj.optString("description", null);
            e.amount = obj.optInt("amount", 0);
            e.usageDateTime = obj.isNull("usageDateTime") ? null : new Date(obj.getLong("usageDateTime"));
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(e);
        }
        return list;
    }

    public static JSONArray usageImagesToJson(List<ItemUsageImage> entries) throws JSONException {
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

    public static List<ItemUsageImage> usageImagesFromJson(JSONArray arr) throws JSONException {
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
}
