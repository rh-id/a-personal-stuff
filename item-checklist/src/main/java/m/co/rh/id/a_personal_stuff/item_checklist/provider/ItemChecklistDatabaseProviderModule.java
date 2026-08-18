package m.co.rh.id.a_personal_stuff.item_checklist.provider;

import android.content.Context;

import androidx.room.Room;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.room.ItemChecklistDatabase;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemChecklistDatabaseProviderModule implements ProviderModule {

    private String mDbName;

    public ItemChecklistDatabaseProviderModule(String dbName) {
        mDbName = dbName;
    }

    public ItemChecklistDatabaseProviderModule() {
        this(Constants.DATABASE_ITEM_CHECKLIST);
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = provider.getContext().getApplicationContext();
        providerRegistry.registerAsync(ItemChecklistDatabase.class, () ->
                Room.databaseBuilder(appContext,
                                ItemChecklistDatabase.class, mDbName)
                        .build());
        // register Dao separately to decouple from Database
        providerRegistry.registerAsync(ItemChecklistDao.class, () -> provider.get(ItemChecklistDatabase.class)
                .itemChecklistDao());
    }
}
