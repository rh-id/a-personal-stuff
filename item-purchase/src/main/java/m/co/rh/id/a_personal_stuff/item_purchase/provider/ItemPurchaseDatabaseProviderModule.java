package m.co.rh.id.a_personal_stuff.item_purchase.provider;

import android.content.Context;

import androidx.room.Room;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.room.ItemPurchaseDatabase;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemPurchaseDatabaseProviderModule implements ProviderModule {

    private String mDbName;

    public ItemPurchaseDatabaseProviderModule(String dbName) {
        mDbName = dbName;
    }

    public ItemPurchaseDatabaseProviderModule() {
        this(Constants.DATABASE_ITEM_PURCHASE);
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = provider.getContext().getApplicationContext();
        providerRegistry.registerAsync(ItemPurchaseDatabase.class, () ->
                Room.databaseBuilder(appContext,
                                ItemPurchaseDatabase.class, mDbName)
                        .build());
        // register Dao separately to decouple from Database
        providerRegistry.registerAsync(ItemPurchaseDao.class, () -> provider.get(ItemPurchaseDatabase.class)
                .itemPurchaseDao());
    }
}
