package m.co.rh.id.a_personal_stuff.item_usage.room;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DbMigration {
    public static Migration[] getAllMigrations() {
        return new Migration[]{MIGRATION_1_2};
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Adds usage_date_time column to item_usage table and backfills
            // existing records with their created_date_time as the usage date
            database.execSQL("ALTER TABLE item_usage ADD COLUMN usage_date_time INTEGER");
            database.execSQL("UPDATE item_usage SET usage_date_time = created_date_time WHERE usage_date_time IS NULL");
        }
    };
}
