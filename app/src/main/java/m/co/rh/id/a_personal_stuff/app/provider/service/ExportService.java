package m.co.rh.id.a_personal_stuff.app.provider.service;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONObject;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.entity.BackupData;
import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.base.backup.ExportImageDir;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleBackupContributor;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleExport;
import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.aprovider.Provider;

/**
 * Stateless backup builder: gathers the base-DB items core plus every
 * module contributor's sections and referenced images into a single zip
 * archive. Holds only final injected collaborators and is safe to share;
 * callers own the threading (the work is blocking).
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
    private final ItemFileHelper mItemFileHelper;
    private final BackupContributorRegistry mContributorRegistry;

    public ExportService(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mFileHelper = provider.get(FileHelper.class);
        mItemDao = provider.get(ItemDao.class);
        mItemFileHelper = provider.get(ItemFileHelper.class);
        mContributorRegistry = provider.get(BackupContributorRegistry.class);
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
        JSONObject root = backupData.toJson();

        // module sections and their referenced image dirs come from the
        // contributors; the base items core and its image dirs stay here
        // (the items anchor is :app-owned)
        List<ExportImageDir> imageDirs = new ArrayList<>();
        Set<String> itemImageFileNames = new HashSet<>();
        for (ItemImage img : backupData.itemImages) {
            if (img.fileName != null) itemImageFileNames.add(img.fileName);
        }
        imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_IMAGE,
                mItemFileHelper.getItemImageParent(), itemImageFileNames));
        imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL,
                mItemFileHelper.getItemImageThumbnailParent(), itemImageFileNames));
        for (ModuleBackupContributor contributor : mContributorRegistry.getBackupContributors()) {
            ModuleExport moduleExport = contributor.exportData();
            moduleExport.mergeInto(root);
            imageDirs.addAll(moduleExport.getImageDirs());
        }

        progress(progressListener, R.string.export_progress_writing);
        String json = root.toString();
        File zipFile = mFileHelper.createTempFile("backup.zip");
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            ZipEntry jsonEntry = new ZipEntry(BACKUP_JSON_ENTRY);
            zos.putNextEntry(jsonEntry);
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            for (int i = 0; i < imageDirs.size(); i++) {
                progress(progressListener, R.string.export_progress_images, i + 1);
                ExportImageDir imageDir = imageDirs.get(i);
                addImageDirToZip(zos, imageDir.getParentDir(),
                        imageDir.getEntryPrefix(), imageDir.getReferencedFileNames());
            }
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
