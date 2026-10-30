package m.co.rh.id.a_personal_stuff.item_purchase.provider.component;

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
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the item-purchase module's backup/export/import behavior
 * (backup.json sections, image dirs, import with id remap and notifier
 * events). JSON field names and top-level keys are byte-compatible with the
 * pre-contract BackupData format.
 */
public class ItemPurchaseBackupContributor implements ModuleBackupContributor {

    private static final String TAG = ItemPurchaseBackupContributor.class.getName();
    private static final String KEY_ITEM_PURCHASES = "itemPurchases";
    private static final String KEY_ITEM_PURCHASE_IMAGES = "itemPurchaseImages";

    private final ILogger mLogger;
    private final FileHelper mFileHelper;
    private final ItemPurchaseDao mItemPurchaseDao;
    private final ItemPurchaseFileHelper mItemPurchaseFileHelper;
    private final ItemPurchaseChangeNotifier mItemPurchaseChangeNotifier;

    public ItemPurchaseBackupContributor(Provider provider) {
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
        mItemPurchaseFileHelper = provider.get(ItemPurchaseFileHelper.class);
        mItemPurchaseChangeNotifier = provider.get(ItemPurchaseChangeNotifier.class);
    }

    @Override
    public List<String> getJsonKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(KEY_ITEM_PURCHASES);
        keys.add(KEY_ITEM_PURCHASE_IMAGES);
        return keys;
    }

    @Override
    public ModuleExport exportData() {
        List<ItemPurchase> itemPurchases = mItemPurchaseDao.findAllItemPurchases();
        List<ItemPurchaseImage> itemPurchaseImages = mItemPurchaseDao.findAllItemPurchaseImages();
        try {
            List<JSONObject> sections = new ArrayList<>();
            sections.add(purchasesSection(itemPurchases));
            sections.add(purchaseImagesSection(itemPurchaseImages));

            List<ExportImageDir> imageDirs = new ArrayList<>();
            imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_PURCHASE_IMAGE,
                    mItemPurchaseFileHelper.getItemPurchaseImageParent(),
                    referencedFileNames(itemPurchaseImages)));
            imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_PURCHASE_IMAGE_THUMBNAIL,
                    mItemPurchaseFileHelper.getItemPurchaseImageThumbnailParent(),
                    referencedFileNames(itemPurchaseImages)));
            return ModuleExport.ofSectionsAndImageDirs(sections, imageDirs);
        } catch (JSONException e) {
            // cannot happen: only primitive values are written
            throw new IllegalStateException("Item purchase backup serialization failed", e);
        }
    }

    @Override
    public void importJson(JSONObject root, Map<Long, Long> oldToNewItemId, File tempDir) throws JSONException {
        List<ItemPurchase> itemPurchases = purchasesFromJson(root.optJSONArray(KEY_ITEM_PURCHASES));
        List<ItemPurchaseImage> itemPurchaseImages = purchaseImagesFromJson(root.optJSONArray(KEY_ITEM_PURCHASE_IMAGES));
        Map<Long, List<ItemPurchaseImage>> purchaseImagesByOldPurchaseId = groupPurchaseImagesByPurchaseId(itemPurchaseImages);
        for (ItemPurchase e : itemPurchases) {
            Long newItemId = oldToNewItemId.get(e.itemId);
            if (newItemId == null) continue;
            long oldId = e.id;
            e.id = null;
            e.itemId = newItemId;
            long newId = mItemPurchaseDao.insert(e);
            e.id = newId;
            List<ItemPurchaseImage> relatedImages = purchaseImagesByOldPurchaseId.getOrDefault(oldId, new ArrayList<>());
            ArrayList<ItemPurchaseImage> purchaseImages = new ArrayList<>();
            for (ItemPurchaseImage ie : relatedImages) {
                ie.id = null;
                ie.itemPurchaseId = newId;
                String oldFileName = null;
                String newFileName = null;
                if (ie.fileName != null && !ie.fileName.isEmpty()) {
                    oldFileName = ie.fileName;
                    newFileName = mFileHelper.generateImageFileName();
                    ie.fileName = newFileName;
                }
                // insert row before copying image files so file helper cleanUp passes never see an orphan file
                ie.id = mItemPurchaseDao.insert(ie);
                if (oldFileName != null) {
                    BackupFileUtils.copyImageFromTemp(mLogger, TAG, tempDir, Constants.FILE_DIR_ITEM_PURCHASE_IMAGE, oldFileName, newFileName, mItemPurchaseFileHelper.getItemPurchaseImageParent());
                    BackupFileUtils.copyImageFromTemp(mLogger, TAG, tempDir, Constants.FILE_DIR_ITEM_PURCHASE_IMAGE_THUMBNAIL, oldFileName, newFileName, mItemPurchaseFileHelper.getItemPurchaseImageThumbnailParent());
                }
                purchaseImages.add(ie);
            }
            ItemPurchaseState purchaseState = new ItemPurchaseState();
            purchaseState.updateItemPurchase(e);
            if (!purchaseImages.isEmpty()) {
                purchaseState.updateItemPurchaseImages(purchaseImages);
            }
            mItemPurchaseChangeNotifier.itemPurchaseAdded(purchaseState.clone());
        }
    }

    private static Set<String> referencedFileNames(List<ItemPurchaseImage> images) {
        Set<String> fileNames = new HashSet<>();
        for (ItemPurchaseImage image : images) {
            if (image.fileName != null) fileNames.add(image.fileName);
        }
        return fileNames;
    }

    private static Map<Long, List<ItemPurchaseImage>> groupPurchaseImagesByPurchaseId(List<ItemPurchaseImage> images) {
        Map<Long, List<ItemPurchaseImage>> map = new HashMap<>();
        for (ItemPurchaseImage img : images) {
            map.computeIfAbsent(img.itemPurchaseId, k -> new ArrayList<>()).add(img);
        }
        return map;
    }

    // Serialization helpers are public static so tests can assemble fixture
    // backups with the exact production field names.

    public static JSONObject purchasesSection(List<ItemPurchase> entries) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ITEM_PURCHASES, purchasesToJson(entries));
        return obj;
    }

    public static JSONObject purchaseImagesSection(List<ItemPurchaseImage> entries) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ITEM_PURCHASE_IMAGES, purchaseImagesToJson(entries));
        return obj;
    }

    public static JSONArray purchasesToJson(List<ItemPurchase> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemPurchase e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemId", e.itemId);
            obj.put("description", e.description);
            obj.put("amount", e.amount);
            obj.put("cost", e.cost != null ? e.cost.toString() : JSONObject.NULL);
            obj.put("purchaseDateTime", e.purchaseDateTime != null ? e.purchaseDateTime.getTime() : JSONObject.NULL);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    public static List<ItemPurchase> purchasesFromJson(JSONArray arr) throws JSONException {
        List<ItemPurchase> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemPurchase e = new ItemPurchase();
            e.id = obj.optLong("id", 0);
            e.itemId = obj.optLong("itemId", 0);
            e.description = obj.optString("description", null);
            e.amount = obj.optInt("amount", 0);
            String costStr = obj.isNull("cost") ? null : obj.optString("cost", null);
            e.cost = costStr != null && !costStr.isEmpty() ? new BigDecimal(costStr) : null;
            e.purchaseDateTime = obj.isNull("purchaseDateTime") ? null : new Date(obj.getLong("purchaseDateTime"));
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(e);
        }
        return list;
    }

    public static JSONArray purchaseImagesToJson(List<ItemPurchaseImage> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemPurchaseImage e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemPurchaseId", e.itemPurchaseId);
            obj.put("fileName", e.fileName);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    public static List<ItemPurchaseImage> purchaseImagesFromJson(JSONArray arr) throws JSONException {
        List<ItemPurchaseImage> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemPurchaseImage e = new ItemPurchaseImage();
            e.id = obj.optLong("id", 0);
            e.itemPurchaseId = obj.optLong("itemPurchaseId", 0);
            e.fileName = obj.optString("fileName", null);
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(e);
        }
        return list;
    }
}
