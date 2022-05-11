package m.co.rh.id.a_personal_stuff.item_usage.provider;

import android.content.Context;

import androidx.room.Room;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.room.ItemUsageDatabase;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemUsageDatabaseProviderModule implements ProviderModule {

    private String mDbName;

    public ItemUsageDatabaseProviderModule(String dbName) {
        mDbName = dbName;
    }

    public ItemUsageDatabaseProviderModule() {
        this(Constants.DATABASE_ITEM_USAGE);
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = provider.getContext().getApplicationContext();
        providerRegistry.registerAsync(ItemUsageDatabase.class, () ->
                Room.databaseBuilder(appContext,
                                ItemUsageDatabase.class, mDbName)
                        .build());
        // register Dao separately to decouple from Database
        providerRegistry.registerAsync(ItemUsageDao.class, () -> provider.get(ItemUsageDatabase.class)
                .itemUsageDao());
    }

    @Override
    public void dispose(Provider provider) {
        // Leave blank
    }
}
