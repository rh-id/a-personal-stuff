package m.co.rh.id.a_personal_stuff.base.provider;

import android.content.Context;

import androidx.room.Room;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.repository.AndroidNotificationRepo;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for database configuration
 */
public class DatabaseProviderModule implements ProviderModule {

    private String mDbName;

    public DatabaseProviderModule(String dbName) {
        mDbName = dbName;
    }

    public DatabaseProviderModule() {
        mDbName = Constants.DATABASE_DEFAULT;
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = provider.getContext().getApplicationContext();
        providerRegistry.registerAsync(AppDatabase.class, () ->
                Room.databaseBuilder(appContext,
                                AppDatabase.class, mDbName)
                        .build());
        // register Dao separately to decouple from AppDatabase
        providerRegistry.registerAsync(AndroidNotificationDao.class, () -> provider.get(AppDatabase.class)
                .androidNotificationDao());
        providerRegistry.registerAsync(ItemDao.class, () -> provider.get(AppDatabase.class)
                .itemDao());

        providerRegistry.registerLazy(AndroidNotificationRepo.class, () ->
                new AndroidNotificationRepo(provider));
    }
}
