package m.co.rh.id.a_personal_stuff.base.room;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DbMigration {
    public static Migration[] getAllMigrations() {
        return new Migration[]{MIGRATION_1_2};
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Adds min_amount column to item table (nullable, null means the
            // low-stock alert is disabled for that item)
            database.execSQL("ALTER TABLE item ADD COLUMN min_amount INTEGER");
        }
    };
}
