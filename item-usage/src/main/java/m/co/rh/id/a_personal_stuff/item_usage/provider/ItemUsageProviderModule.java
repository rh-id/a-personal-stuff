package m.co.rh.id.a_personal_stuff.item_usage.provider;

import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageBackupContributor;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageEventHandler;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageFileHelper;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageSpreadsheetContributor;
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
        // Synchronous contributor registration (never registerAsync): the
        // registry is eager-registered by BaseProviderModule, and :app
        // iterates the contributors after all modules have provided().
        BackupContributorRegistry contributorRegistry = provider.get(BackupContributorRegistry.class);
        contributorRegistry.register(new ItemUsageBackupContributor(provider));
        contributorRegistry.register(new ItemUsageSpreadsheetContributor(provider));
        contributorRegistry.register(new ItemUsageDashboardContributor(provider));
    }
}
