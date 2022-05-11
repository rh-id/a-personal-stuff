package m.co.rh.id.a_personal_stuff.item_maintenance.provider;

import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.DeleteItemMaintenanceCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.DeleteItemMaintenanceImageCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.NewItemMaintenanceCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.NewItemMaintenanceImageCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.PagedItemMaintenanceCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.QueryItemMaintenanceCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.UpdateItemMaintenanceCmd;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemMaintenanceCmdProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(NewItemMaintenanceCmd.class, () -> new NewItemMaintenanceCmd(provider));
        providerRegistry.registerLazy(UpdateItemMaintenanceCmd.class, () -> new UpdateItemMaintenanceCmd(provider));
        providerRegistry.registerLazy(DeleteItemMaintenanceCmd.class, () -> new DeleteItemMaintenanceCmd(provider));
        providerRegistry.registerLazy(NewItemMaintenanceImageCmd.class, () -> new NewItemMaintenanceImageCmd(provider));
        providerRegistry.registerLazy(DeleteItemMaintenanceImageCmd.class, () -> new DeleteItemMaintenanceImageCmd(provider));
        providerRegistry.registerLazy(PagedItemMaintenanceCmd.class, () -> new PagedItemMaintenanceCmd(provider));
        providerRegistry.registerLazy(QueryItemMaintenanceCmd.class, () -> new QueryItemMaintenanceCmd(provider));
    }

    @Override
    public void dispose(Provider provider) {
        // Leave blank
    }
}
