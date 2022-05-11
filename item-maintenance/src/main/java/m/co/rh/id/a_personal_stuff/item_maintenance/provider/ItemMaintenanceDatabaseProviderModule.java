package m.co.rh.id.a_personal_stuff.item_maintenance.provider;

import android.content.Context;

import androidx.room.Room;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.room.ItemMaintenanceDatabase;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemMaintenanceDatabaseProviderModule implements ProviderModule {

    private String mDbName;

    public ItemMaintenanceDatabaseProviderModule(String dbName) {
        mDbName = dbName;
    }

    public ItemMaintenanceDatabaseProviderModule() {
        this(Constants.DATABASE_ITEM_MAINTENANCE);
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = provider.getContext().getApplicationContext();
        providerRegistry.registerAsync(ItemMaintenanceDatabase.class, () ->
                Room.databaseBuilder(appContext,
                                ItemMaintenanceDatabase.class, mDbName)
                        .build());
        // register Dao separately to decouple from Database
        providerRegistry.registerAsync(ItemMaintenanceDao.class, () -> provider.get(ItemMaintenanceDatabase.class)
                .itemMaintenanceDao());
    }

    @Override
    public void dispose(Provider provider) {
        // Leave blank
    }
}
