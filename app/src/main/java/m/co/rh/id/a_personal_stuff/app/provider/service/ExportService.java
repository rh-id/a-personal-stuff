package m.co.rh.id.a_personal_stuff.app.provider.service;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.entity.BackupData;
import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceFileHelper;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchaseFileHelper;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageFileHelper;
import m.co.rh.id.aprovider.Provider;

/**
 * Stateless backup builder: gathers every entity and its referenced images
 * into a single zip archive. Holds only final injected collaborators and is
 * safe to share; callers own the threading (the work is blocking).
 */
public class ExportService {

    /** Receives localized progress messages while a backup zip is built. */
    public interface ProgressListener {
        void onProgress(String message);
    }

    private static final String BACKUP_JSON_ENTRY = "backup.json";
    private static final int BUFFER_SIZE = 2048;

    private final Context mAppContext;
    private final FileHelper mFileHelper;
    private final ItemDao mItemDao;
    private final ItemMaintenanceDao mItemMaintenanceDao;
    private final ItemUsageDao mItemUsageDao;
    private final ItemPurchaseDao mItemPurchaseDao;
    private final ItemReminderDao mItemReminderDao;
    private final ItemChecklistDao mItemChecklistDao;
    private final ItemFileHelper mItemFileHelper;
    private final ItemMaintenanceFileHelper mItemMaintenanceFileHelper;
    private final ItemUsageFileHelper mItemUsageFileHelper;
    private final ItemPurchaseFileHelper mItemPurchaseFileHelper;

    public ExportService(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mFileHelper = provider.get(FileHelper.class);
        mItemDao = provider.get(ItemDao.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
        mItemFileHelper = provider.get(ItemFileHelper.class);
        mItemMaintenanceFileHelper = provider.get(ItemMaintenanceFileHelper.class);
        mItemUsageFileHelper = provider.get(ItemUsageFileHelper.class);
        mItemPurchaseFileHelper = provider.get(ItemPurchaseFileHelper.class);
    }

    /**
     * Builds the full backup zip (backup.json + all referenced images) as a
     * temp file. Blocking; call from a background thread. The returned temp
     * file must be recycled by the caller via
     * {@code FileHelper.deleteTempFile} (or handed to the user).
     *
     * @param progressListener optional; receives localized progress messages
     */
    public File createBackupZip(ProgressListener progressListener) throws Exception {
        progress(progressListener, R.string.export_progress_gathering);
        BackupData backupData = gatherData();
        progress(progressListener, R.string.export_progress_writing);
        String json = backupData.toJson().toString();
        File zipFile = mFileHelper.createTempFile("backup.zip");
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            ZipEntry jsonEntry = new ZipEntry(BACKUP_JSON_ENTRY);
            zos.putNextEntry(jsonEntry);
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            Set<String> itemImageFileNames = new HashSet<>();
            for (ItemImage img : backupData.itemImages) {
                if (img.fileName != null) itemImageFileNames.add(img.fileName);
            }
            Set<String> maintImageFileNames = new HashSet<>();
            for (ItemMaintenanceImage itemMaintenanceImage : backupData.itemMaintenanceImages) {
                if (itemMaintenanceImage.fileName != null) maintImageFileNames.add(itemMaintenanceImage.fileName);
            }
            Set<String> usageImageFileNames = new HashSet<>();
            for (ItemUsageImage itemUsageImage : backupData.itemUsageImages) {
                if (itemUsageImage.fileName != null) usageImageFileNames.add(itemUsageImage.fileName);
            }
            Set<String> purchaseImageFileNames = new HashSet<>();
            for (ItemPurchaseImage itemPurchaseImage : backupData.itemPurchaseImages) {
                if (itemPurchaseImage.fileName != null) purchaseImageFileNames.add(itemPurchaseImage.fileName);
            }
            progress(progressListener, R.string.export_progress_images, 1);
            addImageDirToZip(zos, mItemFileHelper.getItemImageParent(), Constants.FILE_DIR_ITEM_IMAGE, itemImageFileNames);
            progress(progressListener, R.string.export_progress_images, 2);
            addImageDirToZip(zos, mItemMaintenanceFileHelper.getItemMaintenanceImageParent(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE, maintImageFileNames);
            progress(progressListener, R.string.export_progress_images, 3);
            addImageDirToZip(zos, mItemUsageFileHelper.getItemUsageImageParent(), Constants.FILE_DIR_ITEM_USAGE_IMAGE, usageImageFileNames);
            progress(progressListener, R.string.export_progress_images, 4);
            addImageDirToZip(zos, mItemFileHelper.getItemImageThumbnailParent(), Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL, itemImageFileNames);
            progress(progressListener, R.string.export_progress_images, 5);
            addImageDirToZip(zos, mItemMaintenanceFileHelper.getItemMaintenanceImageThumbnailParent(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL, maintImageFileNames);
            progress(progressListener, R.string.export_progress_images, 6);
            addImageDirToZip(zos, mItemUsageFileHelper.getItemUsageImageThumbnailParent(), Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL, usageImageFileNames);
            progress(progressListener, R.string.export_progress_images, 7);
            addImageDirToZip(zos, mItemPurchaseFileHelper.getItemPurchaseImageParent(), Constants.FILE_DIR_ITEM_PURCHASE_IMAGE, purchaseImageFileNames);
            progress(progressListener, R.string.export_progress_images, 8);
            addImageDirToZip(zos, mItemPurchaseFileHelper.getItemPurchaseImageThumbnailParent(), Constants.FILE_DIR_ITEM_PURCHASE_IMAGE_THUMBNAIL, purchaseImageFileNames);
        } finally {
            zos.close();
        }
        return zipFile;
    }

    /**
     * Builds the backup zip without progress reporting. Convenience overload
     * for background callers (e.g. the auto backup worker).
     */
    public File createBackupZip() throws Exception {
        return createBackupZip(null);
    }

    private void progress(ProgressListener listener, int resId, Object... args) {
        if (listener != null) {
            listener.onProgress(mAppContext.getString(resId, args));
        }
    }

    private BackupData gatherData() {
        BackupData data = new BackupData();
        List<ItemState> itemStates = mItemDao.findItemStateWithLimit(Integer.MAX_VALUE, null);
        for (ItemState state : itemStates) {
            data.items.add(state.getItem());
            data.itemImages.addAll(state.getItemImages());
            data.itemTags.addAll(state.getItemTags());
        }
        data.itemMaintenances.addAll(mItemMaintenanceDao.findAllItemMaintenances());
        data.itemMaintenanceImages.addAll(mItemMaintenanceDao.findAllItemMaintenanceImages());
        data.itemUsages.addAll(mItemUsageDao.findAllItemUsages());
        data.itemUsageImages.addAll(mItemUsageDao.findAllItemUsageImages());
        data.itemPurchases.addAll(mItemPurchaseDao.findAllItemPurchases());
        data.itemPurchaseImages.addAll(mItemPurchaseDao.findAllItemPurchaseImages());
        data.itemReminders.addAll(mItemReminderDao.findAllItemReminders());
        data.itemChecklists.addAll(mItemChecklistDao.findAllItemChecklists());
        data.itemChecklistItems.addAll(mItemChecklistDao.findAllItemChecklistItems());
        return data;
    }

    private void addImageDirToZip(ZipOutputStream zos, File imageDir, String entryPrefix, Set<String> referencedFileNames) throws Exception {
        if (imageDir == null || !imageDir.exists()) return;
        File[] files = imageDir.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[BUFFER_SIZE];
        for (File file : files) {
            if (file.isDirectory()) continue;
            if (referencedFileNames != null && !referencedFileNames.contains(file.getName())) continue;
            ZipEntry entry = new ZipEntry(entryPrefix + "/" + file.getName());
            zos.putNextEntry(entry);
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE);
            try {
                int count;
                while ((count = bis.read(buffer, 0, BUFFER_SIZE)) != -1) {
                    zos.write(buffer, 0, count);
                }
            } finally {
                bis.close();
                zos.closeEntry();
            }
        }
    }
}
