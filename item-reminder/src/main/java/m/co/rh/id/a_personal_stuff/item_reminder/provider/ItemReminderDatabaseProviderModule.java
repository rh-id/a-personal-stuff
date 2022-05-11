package m.co.rh.id.a_personal_stuff.item_reminder.provider;

import android.content.Context;

import androidx.room.Room;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.room.ItemReminderDatabase;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemReminderDatabaseProviderModule implements ProviderModule {

    private String mDbName;

    public ItemReminderDatabaseProviderModule(String dbName) {
        mDbName = dbName;
    }

    public ItemReminderDatabaseProviderModule() {
        this(Constants.DATABASE_ITEM_REMINDER);
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = provider.getContext().getApplicationContext();
        providerRegistry.registerAsync(ItemReminderDatabase.class, () ->
                Room.databaseBuilder(appContext,
                                ItemReminderDatabase.class, mDbName)
                        .build());
        // register Dao separately to decouple from Database
        providerRegistry.registerAsync(ItemReminderDao.class, () -> provider.get(ItemReminderDatabase.class)
                .itemReminderDao());
    }

    @Override
    public void dispose(Provider provider) {
        // Leave blank
    }
}
