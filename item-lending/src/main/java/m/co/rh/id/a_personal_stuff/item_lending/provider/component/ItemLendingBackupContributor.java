package m.co.rh.id.a_personal_stuff.item_lending.provider.component;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_personal_stuff.base.contributor.BackupFileUtils;
import m.co.rh.id.a_personal_stuff.base.contributor.ExportImageDir;
import m.co.rh.id.a_personal_stuff.base.contributor.ModuleBackupContributor;
import m.co.rh.id.a_personal_stuff.base.contributor.ModuleExport;
import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLending;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLendingImage;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.a_personal_stuff.item_lending.provider.notifier.ItemLendingChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_lending.workmanager.WorkManagerConstants;
import m.co.rh.id.a_personal_stuff.item_lending.workmanager.worker.ItemLendingReturnNotificationWorker;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the item-lending module's backup/export/import behavior
 * (backup.json sections, image dirs, import with id remap and notifier
 * events). The import regenerates every row's task_id (fresh WorkManager
 * unique-work id) and re-enqueues the return reminder for future-due
 * unreturned lendings, mirroring the item-reminder module's contract.
 */
public class ItemLendingBackupContributor implements ModuleBackupContributor {

    private static final String TAG = ItemLendingBackupContributor.class.getName();
    private static final String KEY_ITEM_LENDINGS = "itemLendings";
    private static final String KEY_ITEM_LENDING_IMAGES = "itemLendingImages";

    private final Provider mProvider;
    private final ILogger mLogger;
    private final FileHelper mFileHelper;
    private final ItemLendingDao mItemLendingDao;
    private final ItemLendingFileHelper mItemLendingFileHelper;
    private final ItemLendingChangeNotifier mItemLendingChangeNotifier;
    private WorkManager mWorkManager;

    public ItemLendingBackupContributor(Provider provider) {
        mProvider = provider;
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mItemLendingDao = provider.get(ItemLendingDao.class);
        mItemLendingFileHelper = provider.get(ItemLendingFileHelper.class);
        mItemLendingChangeNotifier = provider.get(ItemLendingChangeNotifier.class);
    }

    /**
     * Resolved lazily on first import, NOT in the constructor: contributors
     * are constructed inside their module's provides(), i.e. while the
     * provider is still starting. WorkManager.getInstance triggers on-demand
     * initialization, whose Configuration.Provider callback needs the app's
     * provider — still null at that point (crashes startup).
     */
    private WorkManager getWorkManager() {
        if (mWorkManager == null) {
            mWorkManager = mProvider.get(WorkManager.class);
        }
        return mWorkManager;
    }

    @Override
    public List<String> getJsonKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(KEY_ITEM_LENDINGS);
        keys.add(KEY_ITEM_LENDING_IMAGES);
        return keys;
    }

    @Override
    public ModuleExport exportData() {
        List<ItemLending> itemLendings = mItemLendingDao.findAllItemLendings();
        List<ItemLendingImage> itemLendingImages = mItemLendingDao.findAllItemLendingImages();
        try {
            List<JSONObject> sections = new ArrayList<>();
            sections.add(lendingsSection(itemLendings));
            sections.add(lendingImagesSection(itemLendingImages));

            List<ExportImageDir> imageDirs = new ArrayList<>();
            imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_LENDING_IMAGE,
                    mItemLendingFileHelper.getItemLendingImageParent(),
                    referencedFileNames(itemLendingImages)));
            imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_LENDING_IMAGE_THUMBNAIL,
                    mItemLendingFileHelper.getItemLendingImageThumbnailParent(),
                    referencedFileNames(itemLendingImages)));
            return ModuleExport.ofSectionsAndImageDirs(sections, imageDirs);
        } catch (JSONException e) {
            // cannot happen: only primitive values are written
            throw new IllegalStateException("Item lending backup serialization failed", e);
        }
    }

    @Override
    public void importJson(JSONObject root, Map<Long, Long> oldToNewItemId, File tempDir) throws JSONException {
        List<ItemLending> itemLendings = lendingsFromJson(root.optJSONArray(KEY_ITEM_LENDINGS));
        List<ItemLendingImage> itemLendingImages = lendingImagesFromJson(root.optJSONArray(KEY_ITEM_LENDING_IMAGES));
        Map<Long, List<ItemLendingImage>> lendingImagesByOldLendingId = groupLendingImagesByLendingId(itemLendingImages);
        for (ItemLending e : itemLendings) {
            Long newItemId = oldToNewItemId.get(e.itemId);
            if (newItemId == null) continue;
            long oldId = e.id;
            e.id = null;
            e.itemId = newItemId;
            // Handle null lendDateTime from hand-edited backups (validation
            // enforces non-null on app-created rows)
            if (e.lendDateTime == null) {
                e.lendDateTime = new Date();
            }
            // a fresh unique-work id so re-imported lendings cannot collide
            // with stale WorkManager jobs
            e.taskId = UUID.randomUUID().toString();
            long newId = mItemLendingDao.insert(e);
            e.id = newId;
            List<ItemLendingImage> relatedImages = lendingImagesByOldLendingId.getOrDefault(oldId, new ArrayList<>());
            ArrayList<ItemLendingImage> lendingImages = new ArrayList<>();
            for (ItemLendingImage ie : relatedImages) {
                ie.id = null;
                ie.itemLendingId = newId;
                String oldFileName = null;
                String newFileName = null;
                if (ie.fileName != null && !ie.fileName.isEmpty()) {
                    oldFileName = ie.fileName;
                    newFileName = mFileHelper.generateImageFileName();
                    ie.fileName = newFileName;
                }
                // insert row before copying image files so file helper cleanUp passes never see an orphan file
                ie.id = mItemLendingDao.insert(ie);
                if (oldFileName != null) {
                    BackupFileUtils.copyImageFromTemp(mLogger, TAG, tempDir, Constants.FILE_DIR_ITEM_LENDING_IMAGE, oldFileName, newFileName, mItemLendingFileHelper.getItemLendingImageParent());
                    BackupFileUtils.copyImageFromTemp(mLogger, TAG, tempDir, Constants.FILE_DIR_ITEM_LENDING_IMAGE_THUMBNAIL, oldFileName, newFileName, mItemLendingFileHelper.getItemLendingImageThumbnailParent());
                }
                lendingImages.add(ie);
            }
            ItemLendingState lendingState = new ItemLendingState();
            lendingState.updateItemLending(e);
            if (!lendingImages.isEmpty()) {
                lendingState.updateItemLendingImages(lendingImages);
            }
            scheduleReturnReminder(e);
            mItemLendingChangeNotifier.itemLendingAdded(lendingState.clone());
        }
    }

    private static Set<String> referencedFileNames(List<ItemLendingImage> images) {
        Set<String> fileNames = new HashSet<>();
        for (ItemLendingImage image : images) {
            if (image.fileName != null) fileNames.add(image.fileName);
        }
        return fileNames;
    }

    private static Map<Long, List<ItemLendingImage>> groupLendingImagesByLendingId(List<ItemLendingImage> images) {
        Map<Long, List<ItemLendingImage>> map = new HashMap<>();
        for (ItemLendingImage img : images) {
            map.computeIfAbsent(img.itemLendingId, k -> new ArrayList<>()).add(img);
        }
        return map;
    }

    // Serialization helpers are public static so tests can assemble fixture
    // backups with the exact production field names.

    public static JSONObject lendingsSection(List<ItemLending> entries) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ITEM_LENDINGS, lendingsToJson(entries));
        return obj;
    }

    public static JSONObject lendingImagesSection(List<ItemLendingImage> entries) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ITEM_LENDING_IMAGES, lendingImagesToJson(entries));
        return obj;
    }

    public static JSONArray lendingsToJson(List<ItemLending> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemLending e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemId", e.itemId);
            obj.put("taskId", e.taskId);
            obj.put("borrowerName", e.borrowerName);
            obj.put("lendDateTime", e.lendDateTime != null ? e.lendDateTime.getTime() : JSONObject.NULL);
            obj.put("dueDateTime", e.dueDateTime != null ? e.dueDateTime.getTime() : JSONObject.NULL);
            obj.put("returnedDateTime", e.returnedDateTime != null ? e.returnedDateTime.getTime() : JSONObject.NULL);
            obj.put("note", e.note);
            arr.put(obj);
        }
        return arr;
    }

    public static List<ItemLending> lendingsFromJson(JSONArray arr) throws JSONException {
        List<ItemLending> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemLending e = new ItemLending();
            e.id = obj.optLong("id", 0);
            e.itemId = obj.optLong("itemId", 0);
            e.taskId = obj.optString("taskId", null);
            e.borrowerName = obj.optString("borrowerName", null);
            e.lendDateTime = obj.isNull("lendDateTime") ? null : new Date(obj.getLong("lendDateTime"));
            e.dueDateTime = obj.isNull("dueDateTime") ? null : new Date(obj.getLong("dueDateTime"));
            e.returnedDateTime = obj.isNull("returnedDateTime") ? null : new Date(obj.getLong("returnedDateTime"));
            e.note = obj.optString("note", null);
            list.add(e);
        }
        return list;
    }

    public static JSONArray lendingImagesToJson(List<ItemLendingImage> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemLendingImage e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemLendingId", e.itemLendingId);
            obj.put("fileName", e.fileName);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    public static List<ItemLendingImage> lendingImagesFromJson(JSONArray arr) throws JSONException {
        List<ItemLendingImage> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemLendingImage e = new ItemLendingImage();
            e.id = obj.optLong("id", 0);
            e.itemLendingId = obj.optLong("itemLendingId", 0);
            e.fileName = obj.optString("fileName", null);
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(e);
        }
        return list;
    }

    /**
     * Enqueues the lending's return-notification unique work; only future-due
     * unreturned lendings are re-scheduled (returned or past-due lendings fire
     * no notification after a restore).
     */
    private void scheduleReturnReminder(ItemLending itemLending) {
        if (itemLending.dueDateTime == null || itemLending.returnedDateTime != null) return;
        long currentMilis = new Date().getTime();
        long dueMilis = itemLending.dueDateTime.getTime();
        long delay = dueMilis - currentMilis;
        if (delay <= 0) return;
        OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(ItemLendingReturnNotificationWorker.class)
                .setInputData(new Data.Builder()
                        .putLong(WorkManagerConstants.KEY_LONG_ITEM_LENDING_ID, itemLending.id)
                        .build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        getWorkManager().enqueueUniqueWork(itemLending.taskId, ExistingWorkPolicy.KEEP,
                oneTimeWorkRequest);
    }
}
