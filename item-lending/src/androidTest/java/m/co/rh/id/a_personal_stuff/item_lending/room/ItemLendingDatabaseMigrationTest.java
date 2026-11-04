package m.co.rh.id.a_personal_stuff.item_lending.room;

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
public class ItemLendingDatabaseMigrationTest {

    private static final String TEST_DB = "lending-db-migration-test";

    @Rule
    public MigrationTestHelper mMigrationTestHelper =
            new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                    ItemLendingDatabase.class);

    @Test
    public void validateLatestSchemaAndReadBackSeededRows() throws IOException {
        long now = System.currentTimeMillis();
        // The lending DB starts at version 1, so there is no migration path
        // yet: this test pins the v1 schema by creating it, inserting one
        // lending with one image, and re-validating the schema at the latest
        // exported version (the helper validates against 1.json).
        SupportSQLiteDatabase db = mMigrationTestHelper.createDatabase(TEST_DB, 1);
        ContentValues lendingValues = new ContentValues();
        lendingValues.put("id", 1L);
        lendingValues.put("item_id", 10L);
        lendingValues.put("borrower_name", "schema borrower");
        lendingValues.put("lend_date_time", now);
        lendingValues.putNull("due_date_time");
        lendingValues.putNull("returned_date_time");
        lendingValues.putNull("note");
        lendingValues.put("task_id", "schema-test-task");
        db.insert("item_lending", SQLiteDatabase.CONFLICT_FAIL, lendingValues);
        ContentValues imageValues = new ContentValues();
        imageValues.put("id", 1L);
        imageValues.put("item_lending_id", 1L);
        imageValues.put("file_name", "schema_lending.jpg");
        imageValues.put("created_date_time", now);
        db.insert("item_lending_image", SQLiteDatabase.CONFLICT_FAIL, imageValues);
        db.close();

        SupportSQLiteDatabase validated = mMigrationTestHelper.runMigrationsAndValidate(
                TEST_DB, 1, true);
        mMigrationTestHelper.closeWhenFinished(validated);

        try (Cursor cursor = validated.query(
                "SELECT borrower_name, task_id FROM item_lending WHERE id = 1")) {
            assertTrue(cursor.moveToFirst());
            assertFalse(cursor.isNull(0));
            assertEquals("schema borrower", cursor.getString(0));
            assertEquals("schema-test-task", cursor.getString(1));
        }
        try (Cursor cursor = validated.query(
                "SELECT file_name FROM item_lending_image WHERE item_lending_id = 1")) {
            assertEquals(1, cursor.getCount());
        }
    }
}
