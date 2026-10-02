package m.co.rh.id.a_personal_stuff.app.workmanager.worker;

import android.content.ContentResolver;
import android.content.Context;
import android.content.UriPermission;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_personal_stuff.app.provider.service.ExportService;
import m.co.rh.id.a_personal_stuff.app.workmanager.AutoBackupFileUtil;
import m.co.rh.id.a_personal_stuff.base.BaseApplication;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Runs one automatic backup cycle: exports the full database + images to a
 * temp zip (via {@link ExportService}), copies it into the user-selected SAF
 * folder under {@code personal-stuff-backups/}, then rotates old backups so
 * only the newest {@link AutoBackupFileUtil#KEEP_COUNT} remain.
 * <p>
 * Note: this app configures WorkManager with its single-thread executor, so a
 * long backup can delay reminder/digest workers queued behind it — an accepted
 * trade-off for a battery-not-low nightly job.
 */
public class AutoBackupWorker extends Worker {

    private static final String TAG = AutoBackupWorker.class.getName();

    /** Sub-directory (inside the picked tree) that receives the backups. */
    public static final String BACKUP_DIR_NAME = "personal-stuff-backups";
    /**
     * octet-stream keeps the documents provider from appending a bogus
     * extension; rotation matches by {@link AutoBackupFileUtil#BACKUP_FILE_PREFIX}
     * only, not by extension.
     */
    private static final String MIME_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final int COPY_BUFFER_SIZE = 8192;
    /** Temp backup zips older than this are pruned from the app's temp root. */
    private static final long TEMP_BACKUP_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L;

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SettingsSharedPreferences settings = null;
        // kept outside the try so the finally below can clean it up even when
        // the export or any later step fails
        File tempZip = null;
        try {
            Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
            ILogger logger = provider.get(ILogger.class);
            settings = provider.get(SettingsSharedPreferences.class);
            if (!settings.isAutoBackupEnabled()) {
                return Result.success();
            }
            String treeUriString = settings.getAutoBackupTreeUri();
            if (treeUriString.isEmpty()) {
                return Result.success();
            }
            Uri treeUri = Uri.parse(treeUriString);
            // the persisted grant can be revoked by the user or the system at
            // any time; re-check it before writing anything
            if (!hasPersistedWritePermission(treeUri)) {
                return recordFailure(settings, logger,
                        "Write permission for the backup folder is no longer granted, please choose the folder again");
            }
            try {
                tempZip = provider.get(ExportService.class).createBackupZip();
                DocumentFile tree = DocumentFile.fromTreeUri(getApplicationContext(), treeUri);
                if (tree == null || !tree.canWrite()) {
                    return recordFailure(settings, logger,
                            "The selected backup folder is not writable");
                }
                DocumentFile dir = tree.findFile(BACKUP_DIR_NAME);
                if (dir == null || !dir.isDirectory()) {
                    dir = tree.createDirectory(BACKUP_DIR_NAME);
                }
                if (dir == null) {
                    return recordFailure(settings, logger,
                            "Could not create the " + BACKUP_DIR_NAME + " folder");
                }
                String name = AutoBackupFileUtil.backupFileName(new Date());
                DocumentFile target = dir.createFile(MIME_TYPE_OCTET_STREAM, name);
                if (target == null) {
                    return recordFailure(settings, logger,
                            "Could not create the backup file " + name);
                }
                copyFile(tempZip, target.getUri());
                rotateBackups(dir);
                settings.setAutoBackupLastSuccessDateTime(System.currentTimeMillis());
                settings.setAutoBackupLastFailure("");
                return Result.success();
            } finally {
                FileHelper fileHelper = provider.get(FileHelper.class);
                // deleteTempFile is null-safe: on an export failure tempZip
                // is still null and there is simply nothing to delete
                fileHelper.deleteTempFile(tempZip);
                // sweeps zips abandoned by crashed or interrupted runs (also
                // covers manual export/import temp zips sharing the suffix)
                fileHelper.pruneStaleTempFiles(AutoBackupFileUtil.BACKUP_FILE_SUFFIX,
                        TEMP_BACKUP_MAX_AGE_MILLIS);
            }
        } catch (Throwable throwable) {
            ILogger logger = null;
            try {
                logger = BaseApplication.of(getApplicationContext()).getProvider()
                        .get(ILogger.class);
            } catch (Throwable ignored) {
            }
            if (logger != null) {
                logger.e(TAG, throwable.getMessage(), throwable);
            }
            if (settings != null) {
                recordFailure(settings, logger, failureMessage(throwable));
            }
            return Result.failure();
        }
    }

    private boolean hasPersistedWritePermission(Uri treeUri) {
        List<UriPermission> permissions = getApplicationContext().getContentResolver()
                .getPersistedUriPermissions();
        for (UriPermission permission : permissions) {
            if (permission.getUri().equals(treeUri) && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    private void copyFile(File source, Uri targetUri) throws IOException {
        ContentResolver contentResolver = getApplicationContext().getContentResolver();
        OutputStream outputStream = contentResolver.openOutputStream(targetUri);
        if (outputStream == null) {
            throw new IOException("Failed to open output stream for " + targetUri);
        }
        try (OutputStream bufferedOutput = new BufferedOutputStream(outputStream, COPY_BUFFER_SIZE)) {
            try (InputStream inputStream = new BufferedInputStream(
                    new FileInputStream(source), COPY_BUFFER_SIZE)) {
                byte[] buffer = new byte[COPY_BUFFER_SIZE];
                int read = inputStream.read(buffer);
                while (read != -1) {
                    bufferedOutput.write(buffer, 0, read);
                    read = inputStream.read(buffer);
                }
            }
            bufferedOutput.flush();
        }
    }

    private void rotateBackups(DocumentFile dir) {
        DocumentFile[] files = dir.listFiles();
        List<String> fileNames = new ArrayList<>(files.length);
        for (DocumentFile file : files) {
            String fileName = file.getName();
            if (fileName != null) {
                fileNames.add(fileName);
            }
        }
        List<String> toDelete = AutoBackupFileUtil.toDelete(fileNames);
        for (String fileName : toDelete) {
            DocumentFile file = dir.findFile(fileName);
            if (file != null) {
                file.delete();
            }
        }
    }

    private Result recordFailure(SettingsSharedPreferences settings, ILogger logger, String message) {
        try {
            settings.setAutoBackupLastFailure(message);
        } catch (Throwable throwable) {
            if (logger != null) {
                logger.e(TAG, throwable.getMessage(), throwable);
            }
        }
        return Result.failure();
    }

    private static String failureMessage(Throwable throwable) {
        String message = throwable.getMessage();
        String simpleName = throwable.getClass().getSimpleName();
        if (message == null || message.isEmpty()) {
            return simpleName;
        }
        return simpleName + ": " + message;
    }
}
