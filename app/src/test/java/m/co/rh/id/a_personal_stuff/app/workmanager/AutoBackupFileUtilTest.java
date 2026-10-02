package m.co.rh.id.a_personal_stuff.app.workmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class AutoBackupFileUtilTest {

    @Test
    public void backupFileNameUsesFixedFormat() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2024, Calendar.JANUARY, 2, 3, 4, 5);
        String fileName = AutoBackupFileUtil.backupFileName(calendar.getTime());
        assertEquals("personal_stuff_backup_20240102_030405.zip", fileName);
    }

    @Test
    public void toDeleteReturnsAllButNewestFive() {
        List<String> fileNames = new ArrayList<>(Arrays.asList(
                "personal_stuff_backup_20240101_000000.zip",
                "personal_stuff_backup_20240102_000000.zip",
                "personal_stuff_backup_20240103_000000.zip",
                "personal_stuff_backup_20240104_000000.zip",
                "personal_stuff_backup_20240105_000000.zip",
                "personal_stuff_backup_20240106_000000.zip",
                "personal_stuff_backup_20240107_000000.zip"
        ));
        List<String> toDelete = AutoBackupFileUtil.toDelete(fileNames);
        // 7 backups, keep 5 -> the oldest 2 are returned
        assertEquals(Arrays.asList(
                "personal_stuff_backup_20240102_000000.zip",
                "personal_stuff_backup_20240101_000000.zip"
        ), toDelete);
    }

    @Test
    public void toDeleteIgnoresOrderOfInput() {
        List<String> fileNames = new ArrayList<>(Arrays.asList(
                "personal_stuff_backup_20240107_000000.zip",
                "personal_stuff_backup_20240101_000000.zip",
                "personal_stuff_backup_20240105_000000.zip",
                "personal_stuff_backup_20240103_000000.zip",
                "personal_stuff_backup_20240106_000000.zip",
                "personal_stuff_backup_20240102_000000.zip",
                "personal_stuff_backup_20240104_000000.zip"
        ));
        List<String> toDelete = AutoBackupFileUtil.toDelete(fileNames);
        assertEquals(Arrays.asList(
                "personal_stuff_backup_20240102_000000.zip",
                "personal_stuff_backup_20240101_000000.zip"
        ), toDelete);
    }

    @Test
    public void toDeleteWithFewerThanKeepCountReturnsEmpty() {
        List<String> fileNames = new ArrayList<>(Arrays.asList(
                "personal_stuff_backup_20240101_000000.zip",
                "personal_stuff_backup_20240102_000000.zip",
                "personal_stuff_backup_20240103_000000.zip",
                "personal_stuff_backup_20240104_000000.zip"
        ));
        assertTrue(AutoBackupFileUtil.toDelete(fileNames).isEmpty());
    }

    @Test
    public void toDeleteIgnoresNonMatchingNames() {
        List<String> fileNames = new ArrayList<>(Arrays.asList(
                "personal_stuff_backup_20240101_000000.zip",
                "some_other_file.zip",
                "export.zip",
                "personal_stuff_backup_manual.zip"
        ));
        // both prefix-named entries match (timestamped or not), but 2 is
        // within KEEP_COUNT so nothing is deleted; the non-prefix names are
        // ignored entirely
        List<String> toDelete = AutoBackupFileUtil.toDelete(fileNames);
        assertTrue(toDelete.isEmpty());
    }

    @Test
    public void toDeleteWithNullListReturnsEmpty() {
        assertTrue(AutoBackupFileUtil.toDelete(null).isEmpty());
    }

    @Test
    public void toDeleteWithEmptyListReturnsEmpty() {
        assertTrue(AutoBackupFileUtil.toDelete(Collections.emptyList()).isEmpty());
    }
}
