package m.co.rh.id.a_personal_stuff.item_checklist.provider;

import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.component.ItemChecklistBackupContributor;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.component.ItemChecklistEventHandler;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.component.ItemChecklistSpreadsheetContributor;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemChecklistProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new ItemChecklistDatabaseProviderModule());
        providerRegistry.registerLazy(ItemChecklistChangeNotifier.class, ItemChecklistChangeNotifier::new);
        providerRegistry.registerAsync(ItemChecklistEventHandler.class, () -> new ItemChecklistEventHandler(provider));
        // Synchronous contributor registration (never registerAsync): the
        // registry is eager-registered by BaseProviderModule, and :app
        // iterates the contributors after all modules have provided().
        // Checklist changes affect neither the dashboard refresh nor the
        // derived remaining stock, so no dashboard contributor is registered.
        BackupContributorRegistry contributorRegistry = provider.get(BackupContributorRegistry.class);
        contributorRegistry.register(new ItemChecklistBackupContributor(provider));
        contributorRegistry.register(new ItemChecklistSpreadsheetContributor(provider));
    }
}
