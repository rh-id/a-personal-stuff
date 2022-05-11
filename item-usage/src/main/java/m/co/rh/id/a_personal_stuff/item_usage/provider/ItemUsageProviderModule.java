package m.co.rh.id.a_personal_stuff.item_usage.provider;

import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageEventHandler;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageFileHelper;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemUsageProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new ItemUsageDatabaseProviderModule());
        providerRegistry.registerLazy(ItemUsageChangeNotifier.class, ItemUsageChangeNotifier::new);
        providerRegistry.registerAsync(ItemUsageFileHelper.class, () -> new ItemUsageFileHelper(provider));
        providerRegistry.registerAsync(ItemUsageEventHandler.class, () -> new ItemUsageEventHandler(provider));
    }

    @Override
    public void dispose(Provider provider) {
        // Leave blank
    }
}
