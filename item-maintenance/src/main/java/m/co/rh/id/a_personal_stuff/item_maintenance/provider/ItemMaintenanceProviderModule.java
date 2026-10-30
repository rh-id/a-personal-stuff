package m.co.rh.id.a_personal_stuff.item_maintenance.provider;

import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceBackupContributor;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceEventHandler;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceFileHelper;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceSpreadsheetContributor;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.notifier.ItemMaintenanceChangeNotifier;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemMaintenanceProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new ItemMaintenanceDatabaseProviderModule());
        providerRegistry.registerLazy(ItemMaintenanceChangeNotifier.class, ItemMaintenanceChangeNotifier::new);

        providerRegistry.registerAsync(ItemMaintenanceFileHelper.class, () -> new ItemMaintenanceFileHelper(provider));
        providerRegistry.registerAsync(ItemMaintenanceEventHandler.class, () -> new ItemMaintenanceEventHandler(provider));
        // Synchronous contributor registration (never registerAsync): the
        // registry is eager-registered by BaseProviderModule, and :app
        // iterates the contributors after all modules have provided().
        // Maintenance affects neither the dashboard refresh nor the derived
        // remaining stock, so no dashboard contributor is registered.
        BackupContributorRegistry contributorRegistry = provider.get(BackupContributorRegistry.class);
        contributorRegistry.register(new ItemMaintenanceBackupContributor(provider));
        contributorRegistry.register(new ItemMaintenanceSpreadsheetContributor(provider));
    }
}
