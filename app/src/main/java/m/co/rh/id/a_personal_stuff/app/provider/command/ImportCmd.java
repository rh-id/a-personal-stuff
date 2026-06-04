package m.co.rh.id.a_personal_stuff.app.provider.command;

import android.content.Context;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONObject;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.app.entity.BackupData;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceFileHelper;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.notifier.ItemMaintenanceChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.notifier.ItemReminderChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_reminder.workmanager.WorkManagerConstants;
import m.co.rh.id.a_personal_stuff.item_reminder.workmanager.worker.ItemReminderNotificationWorker;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageFileHelper;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class ImportCmd {
    private static final String TAG = ImportCmd.class.getName();
    private static final String BACKUP_JSON_ENTRY = "backup.json";
    private static final int BUFFER_SIZE = 2048;

    private final ExecutorService mExecutorService;
    private final Context mAppContext;
    private final ILogger mLogger;
    private final FileHelper mFileHelper;
    private final ItemDao mItemDao;
    private final ItemMaintenanceDao mItemMaintenanceDao;
    private final ItemUsageDao mItemUsageDao;
    private final ItemReminderDao mItemReminderDao;
    private final ItemFileHelper mItemFileHelper;
    private final ItemMaintenanceFileHelper mItemMaintenanceFileHelper;
    private final ItemUsageFileHelper mItemUsageFileHelper;
    private final WorkManager mWorkManager;
    private final ItemChangeNotifier mItemChangeNotifier;
    private final ItemMaintenanceChangeNotifier mItemMaintenanceChangeNotifier;
    private final ItemUsageChangeNotifier mItemUsageChangeNotifier;
    private final ItemReminderChangeNotifier mItemReminderChangeNotifier;
    private final Subject<String> mProgressSubject = PublishSubject.create();

    public ImportCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mItemDao = provider.get(ItemDao.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mItemFileHelper = provider.get(ItemFileHelper.class);
        mItemMaintenanceFileHelper = provider.get(ItemMaintenanceFileHelper.class);
        mItemUsageFileHelper = provider.get(ItemUsageFileHelper.class);
        mWorkManager = provider.get(WorkManager.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
        mItemMaintenanceChangeNotifier = provider.get(ItemMaintenanceChangeNotifier.class);
        mItemUsageChangeNotifier = provider.get(ItemUsageChangeNotifier.class);
        mItemReminderChangeNotifier = provider.get(ItemReminderChangeNotifier.class);
    }

    public Flowable<String> getProgressFlow() {
        return Flowable.fromObservable(mProgressSubject, BackpressureStrategy.BUFFER);
    }

    public Single<Integer> execute(File backupFile) {
        return Single.fromCallable(() -> {
            mProgressSubject.onNext(mAppContext.getString(R.string.import_progress_extracting));
            File tempDir = mFileHelper.createTempDir();
            try {
                extractZip(backupFile, tempDir);
                mProgressSubject.onNext(mAppContext.getString(R.string.import_progress_reading));
                File jsonFile = new File(tempDir, BACKUP_JSON_ENTRY);
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                JSONObject jsonObj = new JSONObject(sb.toString());
                BackupData data = BackupData.fromJson(jsonObj);
                int count = importData(data, tempDir);
                return count;
            } finally {
                deleteRecursive(tempDir);
            }
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    private void extractZip(File zipFile, File destDir) throws Exception {
        String destDirCanonical = destDir.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[BUFFER_SIZE];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            File outFile = new File(destDir, entry.getName());
            if (!outFile.getCanonicalPath().startsWith(destDirCanonical)) {
                zis.closeEntry();
                continue;
            }
            if (entry.isDirectory()) {
                outFile.mkdirs();
            } else {
                outFile.getParentFile().mkdirs();
                FileOutputStream fos = new FileOutputStream(outFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE);
                try {
                    int count;
                    while ((count = zis.read(buffer)) != -1) {
                        bos.write(buffer, 0, count);
                    }
                } finally {
                    bos.flush();
                    bos.close();
                    fos.close();
                }
            }
            zis.closeEntry();
        }
        zis.close();
    }

    private int importData(BackupData data, File tempDir) {
        Map<Long, Long> oldToNewItemId = new HashMap<>();
        Map<Long, Long> oldToNewMaintenanceId = new HashMap<>();
        Map<Long, Long> oldToNewUsageId = new HashMap<>();
        Map<Long, List<ItemImage>> imagesByOldItemId = groupImagesByItemId(data.itemImages);
        Map<Long, List<ItemTag>> tagsByOldItemId = groupTagsByItemId(data.itemTags);
        Map<Long, List<ItemMaintenanceImage>> maintImagesByOldMaintId = groupMaintImagesByMaintId(data.itemMaintenanceImages);
        Map<Long, List<ItemUsageImage>> usageImagesByOldUsageId = groupUsageImagesByUsageId(data.itemUsageImages);
        int count = 0;
        for (Item item : data.items) {
            long oldId = item.id;
            item.id = null;
            List<ItemImage> relatedImages = imagesByOldItemId.getOrDefault(oldId, new ArrayList<>());
            List<ItemTag> relatedTags = tagsByOldItemId.getOrDefault(oldId, new ArrayList<>());
            Map<String, String> imageRenameMap = new HashMap<>();
            for (ItemImage img : relatedImages) {
                img.id = null;
                img.itemId = null;
                if (img.fileName != null && !img.fileName.isEmpty()) {
                    String oldFileName = img.fileName;
                    String newFileName = mFileHelper.generateImageFileName();
                    img.fileName = newFileName;
                    imageRenameMap.put(newFileName, oldFileName);
                }
            }
            for (ItemTag tag : relatedTags) {
                tag.id = null;
                tag.itemId = null;
            }
            ItemState itemState = new ItemState();
            itemState.updateItem(item);
            if (!relatedImages.isEmpty()) {
                itemState.updateItemImages(relatedImages);
            }
            if (!relatedTags.isEmpty()) {
                itemState.updateItemTags(relatedTags);
            }
            mItemDao.insertItem(itemState);
            long newId = itemState.getItemId();
            oldToNewItemId.put(oldId, newId);
            for (ItemImage img : relatedImages) {
                String oldFileName = imageRenameMap.get(img.fileName);
                if (oldFileName != null) {
                    copyImageFromTemp(tempDir, Constants.FILE_DIR_ITEM_IMAGE, oldFileName, img.fileName, mItemFileHelper.getItemImageParent());
                    copyImageFromTemp(tempDir, Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL, oldFileName, img.fileName, mItemFileHelper.getItemImageThumbnailParent());
                }
            }
            mItemChangeNotifier.itemAdded(itemState.clone());
            count++;
            mProgressSubject.onNext(mAppContext.getString(R.string.import_progress_importing, count, data.items.size()));
        }
        for (ItemMaintenance e : data.itemMaintenances) {
            Long newItemId = oldToNewItemId.get(e.itemId);
            if (newItemId == null) continue;
            long oldId = e.id;
            e.id = null;
            e.itemId = newItemId;
            long newId = mItemMaintenanceDao.insert(e);
            e.id = newId;
            oldToNewMaintenanceId.put(oldId, newId);
            List<ItemMaintenanceImage> relatedImages = maintImagesByOldMaintId.getOrDefault(oldId, new ArrayList<>());
            ArrayList<ItemMaintenanceImage> maintImages = new ArrayList<>();
            for (ItemMaintenanceImage ie : relatedImages) {
                ie.id = null;
                ie.itemMaintenanceId = newId;
                if (ie.fileName != null && !ie.fileName.isEmpty()) {
                    String oldFileName = ie.fileName;
                    String newFileName = mFileHelper.generateImageFileName();
                    ie.fileName = newFileName;
                    copyImageFromTemp(tempDir, Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE, oldFileName, newFileName, mItemMaintenanceFileHelper.getItemMaintenanceImageParent());
                    copyImageFromTemp(tempDir, Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL, oldFileName, newFileName, mItemMaintenanceFileHelper.getItemMaintenanceImageThumbnailParent());
                }
                ie.id = mItemMaintenanceDao.insert(ie);
                maintImages.add(ie);
            }
            ItemMaintenanceState maintState = new ItemMaintenanceState();
            maintState.updateItemMaintenance(e);
            if (!maintImages.isEmpty()) {
                maintState.updateItemMaintenanceImages(maintImages);
            }
            mItemMaintenanceChangeNotifier.itemMaintenanceAdded(maintState.clone());
        }
        for (ItemUsage e : data.itemUsages) {
            Long newItemId = oldToNewItemId.get(e.itemId);
            if (newItemId == null) continue;
            long oldId = e.id;
            e.id = null;
            e.itemId = newItemId;
            long newId = mItemUsageDao.insert(e);
            e.id = newId;
            oldToNewUsageId.put(oldId, newId);
            List<ItemUsageImage> relatedImages = usageImagesByOldUsageId.getOrDefault(oldId, new ArrayList<>());
            ArrayList<ItemUsageImage> usageImages = new ArrayList<>();
            for (ItemUsageImage ie : relatedImages) {
                ie.id = null;
                ie.itemUsageId = newId;
                if (ie.fileName != null && !ie.fileName.isEmpty()) {
                    String oldFileName = ie.fileName;
                    String newFileName = mFileHelper.generateImageFileName();
                    ie.fileName = newFileName;
                    copyImageFromTemp(tempDir, Constants.FILE_DIR_ITEM_USAGE_IMAGE, oldFileName, newFileName, mItemUsageFileHelper.getItemUsageImageParent());
                    copyImageFromTemp(tempDir, Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL, oldFileName, newFileName, mItemUsageFileHelper.getItemUsageImageThumbnailParent());
                }
                ie.id = mItemUsageDao.insert(ie);
                usageImages.add(ie);
            }
            ItemUsageState usageState = new ItemUsageState();
            usageState.updateItemUsage(e);
            if (!usageImages.isEmpty()) {
                usageState.updateItemUsageImages(usageImages);
            }
            mItemUsageChangeNotifier.itemUsageAdded(usageState.clone());
        }
        for (ItemReminder e : data.itemReminders) {
            Long newItemId = oldToNewItemId.get(e.itemId);
            if (newItemId == null) continue;
            e.id = null;
            e.itemId = newItemId;
            e.taskId = UUID.randomUUID().toString();
            mItemReminderDao.insertItemReminder(e);
            scheduleReminder(e);
            mItemReminderChangeNotifier.added(e);
        }
        return count;
    }

    private Map<Long, List<ItemImage>> groupImagesByItemId(List<ItemImage> images) {
        Map<Long, List<ItemImage>> map = new HashMap<>();
        for (ItemImage img : images) {
            map.computeIfAbsent(img.itemId, k -> new ArrayList<>()).add(img);
        }
        return map;
    }

    private Map<Long, List<ItemTag>> groupTagsByItemId(List<ItemTag> tags) {
        Map<Long, List<ItemTag>> map = new HashMap<>();
        for (ItemTag tag : tags) {
            map.computeIfAbsent(tag.itemId, k -> new ArrayList<>()).add(tag);
        }
        return map;
    }

    private Map<Long, List<ItemMaintenanceImage>> groupMaintImagesByMaintId(List<ItemMaintenanceImage> images) {
        Map<Long, List<ItemMaintenanceImage>> map = new HashMap<>();
        for (ItemMaintenanceImage img : images) {
            map.computeIfAbsent(img.itemMaintenanceId, k -> new ArrayList<>()).add(img);
        }
        return map;
    }

    private Map<Long, List<ItemUsageImage>> groupUsageImagesByUsageId(List<ItemUsageImage> images) {
        Map<Long, List<ItemUsageImage>> map = new HashMap<>();
        for (ItemUsageImage img : images) {
            map.computeIfAbsent(img.itemUsageId, k -> new ArrayList<>()).add(img);
        }
        return map;
    }

    private void copyImageFromTemp(File tempDir, String dirName, String oldFileName, String newFileName, File destParent) {
        if (oldFileName == null || oldFileName.isEmpty()) return;
        File srcFile = new File(tempDir, dirName + "/" + oldFileName);
        if (!srcFile.exists()) return;
        try {
            File destFile = new File(destParent, newFileName);
            destParent.mkdirs();
            FileInputStream fis = new FileInputStream(srcFile);
            FileOutputStream fos = new FileOutputStream(destFile);
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, count);
            }
            fos.flush();
            fos.close();
            fis.close();
        } catch (Exception ex) {
            mLogger.e(TAG, "Failed to copy image: " + oldFileName, ex);
        }
    }

    private void scheduleReminder(ItemReminder itemReminder) {
        if (itemReminder.reminderDateTime == null) return;
        long currentMilis = new Date().getTime();
        long reminderMilis = itemReminder.reminderDateTime.getTime();
        long delay = reminderMilis - currentMilis;
        if (delay <= 0) return;
        OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(ItemReminderNotificationWorker.class)
                .setInputData(new Data.Builder()
                        .putLong(WorkManagerConstants.KEY_LONG_ITEM_REMINDER_ID, itemReminder.id)
                        .build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        mWorkManager.enqueueUniqueWork(itemReminder.taskId, ExistingWorkPolicy.KEEP,
                oneTimeWorkRequest);
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}