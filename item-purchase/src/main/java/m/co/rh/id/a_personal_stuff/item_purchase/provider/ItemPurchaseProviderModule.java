package m.co.rh.id.a_personal_stuff.item_purchase.provider;

import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchaseBackupContributor;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchaseDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchaseEventHandler;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchaseFileHelper;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchaseSpreadsheetContributor;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemPurchaseProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new ItemPurchaseDatabaseProviderModule());
        providerRegistry.registerLazy(ItemPurchaseChangeNotifier.class, ItemPurchaseChangeNotifier::new);
        providerRegistry.registerAsync(ItemPurchaseFileHelper.class, () -> new ItemPurchaseFileHelper(provider));
        providerRegistry.registerAsync(ItemPurchaseEventHandler.class, () -> new ItemPurchaseEventHandler(provider));
        // Synchronous contributor registration (never registerAsync): the
        // registry is eager-registered by BaseProviderModule, and :app
        // iterates the contributors after all modules have provided().
        BackupContributorRegistry contributorRegistry = provider.get(BackupContributorRegistry.class);
        contributorRegistry.register(new ItemPurchaseBackupContributor(provider));
        contributorRegistry.register(new ItemPurchaseSpreadsheetContributor(provider));
        contributorRegistry.register(new ItemPurchaseDashboardContributor(provider));
    }
}
