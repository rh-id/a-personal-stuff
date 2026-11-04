package m.co.rh.id.a_personal_stuff.item_lending.provider;

import m.co.rh.id.a_personal_stuff.base.contributor.ModuleContributorRegistry;
import m.co.rh.id.a_personal_stuff.item_lending.provider.component.ItemLendingBackupContributor;
import m.co.rh.id.a_personal_stuff.item_lending.provider.component.ItemLendingDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_lending.provider.component.ItemLendingEventHandler;
import m.co.rh.id.a_personal_stuff.item_lending.provider.component.ItemLendingFileHelper;
import m.co.rh.id.a_personal_stuff.item_lending.provider.component.ItemLendingSpreadsheetContributor;
import m.co.rh.id.a_personal_stuff.item_lending.provider.notifier.ItemLendingChangeNotifier;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemLendingProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new ItemLendingDatabaseProviderModule());
        providerRegistry.registerLazy(ItemLendingChangeNotifier.class, ItemLendingChangeNotifier::new);
        providerRegistry.registerAsync(ItemLendingFileHelper.class, () -> new ItemLendingFileHelper(provider));
        providerRegistry.registerAsync(ItemLendingEventHandler.class, () -> new ItemLendingEventHandler(provider));
        // Synchronous contributor registration (never registerAsync): the
        // registry is eager-registered by BaseProviderModule, and :app
        // iterates the contributors after all modules have provided().
        ModuleContributorRegistry contributorRegistry = provider.get(ModuleContributorRegistry.class);
        contributorRegistry.register(new ItemLendingBackupContributor(provider));
        contributorRegistry.register(new ItemLendingSpreadsheetContributor(provider));
        contributorRegistry.register(new ItemLendingDashboardContributor(provider));
    }
}
