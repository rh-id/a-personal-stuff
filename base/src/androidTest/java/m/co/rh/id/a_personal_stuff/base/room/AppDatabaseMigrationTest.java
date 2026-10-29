package m.co.rh.id.a_personal_stuff.base.room;

import static org.junit.Assert.assertEquals;
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
public class AppDatabaseMigrationTest {

    private static final String TEST_DB = "app-db-migration-test";

    @Rule
    public MigrationTestHelper mMigrationTestHelper =
            new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                    AppDatabase.class);

    @Test
    public void migrate1To2AddsMinAmount() throws IOException {
        SupportSQLiteDatabase db = mMigrationTestHelper.createDatabase(TEST_DB, 1);
        ContentValues values = new ContentValues();
        values.put("id", 1L);
        values.put("name", "legacy item");
        values.put("amount", 5);
        values.put("created_date_time", System.currentTimeMillis());
        values.put("updated_date_time", System.currentTimeMillis());
        db.insert("item", SQLiteDatabase.CONFLICT_FAIL, values);
        db.close();

        // validates the migrated schema against the exported 2.json as well
        SupportSQLiteDatabase migrated = mMigrationTestHelper.runMigrationsAndValidate(
                TEST_DB, 2, true, DbMigration.MIGRATION_1_2);
        mMigrationTestHelper.closeWhenFinished(migrated);

        // existing rows must get a null min_amount (null means the low-stock
        // alert is disabled for that item)
        try (Cursor cursor = migrated.query("SELECT min_amount FROM item WHERE id = 1")) {
            assertTrue(cursor.moveToFirst());
            assertTrue(cursor.isNull(0));
        }
        // and the new column must be writable/readable
        migrated.execSQL("UPDATE item SET min_amount = 3 WHERE id = 1");
        try (Cursor cursor = migrated.query("SELECT min_amount FROM item WHERE id = 1")) {
            assertTrue(cursor.moveToFirst());
            assertEquals(3, cursor.getInt(0));
        }
    }
}
