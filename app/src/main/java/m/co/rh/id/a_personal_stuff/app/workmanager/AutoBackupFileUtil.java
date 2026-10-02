package m.co.rh.id.a_personal_stuff.app.workmanager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Pure-JVM helpers for the automatic scheduled backup file naming and
 * rotation. No Android framework classes here so the logic is unit-testable.
 */
public final class AutoBackupFileUtil {

    /** All auto backup files in the destination folder start with this prefix. */
    public static final String BACKUP_FILE_PREFIX = "personal_stuff_backup_";

    /**
     * Suffix shared by every auto backup zip, temp or final.
     * <p>
     * Files created before v2.2 used the .aps_backup suffix; rotation matches
     * by prefix, not suffix, so they keep rotating.
     */
    public static final String BACKUP_FILE_SUFFIX = ".zip";

    /** Number of newest backup files to keep in the destination folder. */
    public static final int KEEP_COUNT = 5;

    private AutoBackupFileUtil() {
    }

    /**
     * Builds the backup file name for the given date, e.g.
     * {@code personal_stuff_backup_20240102_030405.zip}.
     * {@link Locale#US} is used deliberately so the digits/format are
     * locale-independent and the names sort chronologically.
     */
    public static String backupFileName(Date date) {
        SimpleDateFormat format =
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        return BACKUP_FILE_PREFIX + format.format(date) + BACKUP_FILE_SUFFIX;
    }

    /**
     * Given the file names currently in the backup folder, returns the ones to
     * delete: everything matching {@link #BACKUP_FILE_PREFIX} except the newest
     * {@link #KEEP_COUNT}. The timestamp embedded in the name is
     * lexicographically sortable, so a descending name sort is a descending
     * time sort.
     *
     * @param fileNames names of the files currently in the backup folder
     * @return names to delete (newest first); empty when nothing needs deleting
     */
    public static List<String> toDelete(List<String> fileNames) {
        List<String> backups = new ArrayList<>();
        if (fileNames == null) {
            return backups;
        }
        for (String fileName : fileNames) {
            if (fileName != null && fileName.startsWith(BACKUP_FILE_PREFIX)) {
                backups.add(fileName);
            }
        }
        if (backups.size() <= KEEP_COUNT) {
            backups.clear();
            return backups;
        }
        // newest first; timestamp-in-name sorts lexicographically
        Collections.sort(backups, Collections.reverseOrder());
        return new ArrayList<>(backups.subList(KEEP_COUNT, backups.size()));
    }
}
