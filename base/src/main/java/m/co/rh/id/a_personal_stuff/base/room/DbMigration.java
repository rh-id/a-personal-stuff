package m.co.rh.id.a_personal_stuff.base.room;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DbMigration {
    public static Migration[] getAllMigrations() {
        return new Migration[]{MIGRATION_1_2, MIGRATION_2_3};
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Adds min_amount column to item table (nullable, null means the
            // low-stock alert is disabled for that item)
            database.execSQL("ALTER TABLE item ADD COLUMN min_amount INTEGER");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Adds archived column to item table (0 = active, 1 = archived;
            // archived rows stay in the item table and are just hidden from
            // the active-item queries)
            database.execSQL("ALTER TABLE item ADD COLUMN archived INTEGER NOT NULL DEFAULT 0");
        }
    };
}
