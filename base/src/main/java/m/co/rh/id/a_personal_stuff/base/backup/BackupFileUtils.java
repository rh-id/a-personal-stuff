package m.co.rh.id.a_personal_stuff.base.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import m.co.rh.id.alogger.ILogger;

/**
 * Shared import-time file plumbing for backup contributors.
 */
public final class BackupFileUtils {

    private static final int BUFFER_SIZE = 2048;

    private BackupFileUtils() {
    }

    /**
     * Copies one image out of the extracted backup temp dir to its final
     * location, renaming {@code oldFileName} to {@code newFileName}.
     * Failures are logged and skipped (the row is already inserted, matching
     * the pre-contract behavior); a no-op when the source file is absent.
     */
    public static void copyImageFromTemp(ILogger logger, String tag, File tempDir,
                                         String dirName, String oldFileName,
                                         String newFileName, File destParent) {
        if (oldFileName == null || oldFileName.isEmpty()) {
            return;
        }
        File srcFile = new File(tempDir, dirName + "/" + oldFileName);
        if (!srcFile.exists()) {
            return;
        }
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
            logger.e(tag, "Failed to copy image: " + oldFileName, ex);
        }
    }
}
