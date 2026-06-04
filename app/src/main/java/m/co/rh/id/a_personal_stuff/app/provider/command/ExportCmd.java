package m.co.rh.id.a_personal_stuff.app.provider.command;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageFileHelper;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceFileHelper;
import m.co.rh.id.aprovider.Provider;

public class ExportCmd {
    private static final String BACKUP_JSON_ENTRY = "backup.json";
    private static final int BUFFER_SIZE = 2048;

    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final FileHelper mFileHelper;
    private final Subject<String> mProgressSubject = PublishSubject.create();
    private final ItemDao mItemDao;
    private final ItemMaintenanceDao mItemMaintenanceDao;
    private final ItemUsageDao mItemUsageDao;
    private final ItemReminderDao mItemReminderDao;
    private final ItemFileHelper mItemFileHelper;
    private final ItemMaintenanceFileHelper mItemMaintenanceFileHelper;
    private final ItemUsageFileHelper mItemUsageFileHelper;

    public ExportCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mFileHelper = provider.get(FileHelper.class);
        mItemDao = provider.get(ItemDao.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mItemFileHelper = provider.get(ItemFileHelper.class);
        mItemMaintenanceFileHelper = provider.get(ItemMaintenanceFileHelper.class);
        mItemUsageFileHelper = provider.get(ItemUsageFileHelper.class);
    }

    public Flowable<String> getProgressFlow() {
        return Flowable.fromObservable(mProgressSubject, BackpressureStrategy.BUFFER);
    }

    public Single<File> execute() {
        return Single.fromCallable(() -> {
            mProgressSubject.onNext(mAppContext.getString(R.string.export_progress_gathering));
            BackupData backupData = gatherData();
            mProgressSubject.onNext(mAppContext.getString(R.string.export_progress_writing));
            String json = backupData.toJson().toString();
            File zipFile = mFileHelper.createTempFile("backup.aps_backup");
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
                mProgressSubject.onNext(mAppContext.getString(R.string.export_progress_images, 1));
                addImageDirToZip(zos, mItemFileHelper.getItemImageParent(), Constants.FILE_DIR_ITEM_IMAGE, itemImageFileNames);
                mProgressSubject.onNext(mAppContext.getString(R.string.export_progress_images, 2));
                addImageDirToZip(zos, mItemMaintenanceFileHelper.getItemMaintenanceImageParent(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE, maintImageFileNames);
                mProgressSubject.onNext(mAppContext.getString(R.string.export_progress_images, 3));
                addImageDirToZip(zos, mItemUsageFileHelper.getItemUsageImageParent(), Constants.FILE_DIR_ITEM_USAGE_IMAGE, usageImageFileNames);
                mProgressSubject.onNext(mAppContext.getString(R.string.export_progress_images, 4));
                addImageDirToZip(zos, mItemFileHelper.getItemImageThumbnailParent(), Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL, itemImageFileNames);
                mProgressSubject.onNext(mAppContext.getString(R.string.export_progress_images, 5));
                addImageDirToZip(zos, mItemMaintenanceFileHelper.getItemMaintenanceImageThumbnailParent(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL, maintImageFileNames);
                mProgressSubject.onNext(mAppContext.getString(R.string.export_progress_images, 6));
                addImageDirToZip(zos, mItemUsageFileHelper.getItemUsageImageThumbnailParent(), Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL, usageImageFileNames);
            } finally {
                zos.close();
            }
            return zipFile;
        }).subscribeOn(Schedulers.from(mExecutorService));
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
        data.itemReminders.addAll(mItemReminderDao.findAllItemReminders());
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