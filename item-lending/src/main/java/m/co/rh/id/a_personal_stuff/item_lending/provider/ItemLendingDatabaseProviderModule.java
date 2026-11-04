package m.co.rh.id.a_personal_stuff.item_lending.provider;

import android.content.Context;

import androidx.room.Room;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.room.ItemLendingDatabase;
import m.co.rh.id.a_personal_stuff.item_lending.room.DbMigration;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemLendingDatabaseProviderModule implements ProviderModule {

    private String mDbName;

    public ItemLendingDatabaseProviderModule(String dbName) {
        mDbName = dbName;
    }

    public ItemLendingDatabaseProviderModule() {
        this(Constants.DATABASE_ITEM_LENDING);
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = provider.getContext().getApplicationContext();
        providerRegistry.registerAsync(ItemLendingDatabase.class, () ->
                Room.databaseBuilder(appContext,
                                ItemLendingDatabase.class, mDbName)
                        .addMigrations(DbMigration.getAllMigrations())
                        .build());
        // register Dao separately to decouple from Database
        providerRegistry.registerAsync(ItemLendingDao.class, () -> provider.get(ItemLendingDatabase.class)
                .itemLendingDao());
    }
}
