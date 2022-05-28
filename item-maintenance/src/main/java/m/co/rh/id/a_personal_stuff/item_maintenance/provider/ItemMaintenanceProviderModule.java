package m.co.rh.id.a_personal_stuff.item_maintenance.provider;

import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceEventHandler;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceFileHelper;
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
    }
}
