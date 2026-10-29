package m.co.rh.id.a_personal_stuff.item_usage.room;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class ItemUsageDatabaseMigrationTest {

    private static final String TEST_DB = "usage-db-migration-test";

    @Rule
    public MigrationTestHelper mMigrationTestHelper =
            new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                    ItemUsageDatabase.class);

    @Test
    public void migrate1To2AddsAndBackfillsUsageDateTime() throws IOException {
        long createdDateTime = System.currentTimeMillis();
        SupportSQLiteDatabase db = mMigrationTestHelper.createDatabase(TEST_DB, 1);
        ContentValues values = new ContentValues();
        values.put("id", 1L);
        values.put("item_id", 10L);
        values.put("description", "legacy usage");
        values.put("amount", 2);
        values.put("created_date_time", createdDateTime);
        db.insert("item_usage", SQLiteDatabase.CONFLICT_FAIL, values);
        db.close();

        // validates the migrated schema against the exported 2.json as well
        SupportSQLiteDatabase migrated = mMigrationTestHelper.runMigrationsAndValidate(
                TEST_DB, 2, true, DbMigration.MIGRATION_1_2);
        mMigrationTestHelper.closeWhenFinished(migrated);

        // existing rows must have usage_date_time backfilled from
        // created_date_time
        try (Cursor cursor = migrated.query(
                "SELECT usage_date_time, created_date_time FROM item_usage WHERE id = 1")) {
            assertTrue(cursor.moveToFirst());
            assertFalse(cursor.isNull(0));
            assertEquals(cursor.getLong(1), cursor.getLong(0));
        }
    }
}
