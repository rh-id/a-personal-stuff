package m.co.rh.id.a_personal_stuff.item_usage.room;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Migration from version 1 to 2.
 * Adds usage_date_time column to item_usage table and backfills existing records
 * with their created_date_time as the usage date.
 */
public class Migration1To2 extends Migration {
    public Migration1To2() {
        super(1, 2);
    }

    @Override
    public void migrate(SupportSQLiteDatabase database) {
        // Add the new column
        database.execSQL("ALTER TABLE item_usage ADD COLUMN usage_date_time INTEGER");

        // Backfill existing records with created_date_time
        database.execSQL("UPDATE item_usage SET usage_date_time = created_date_time WHERE usage_date_time IS NULL");
    }
}
