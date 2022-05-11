package m.co.rh.id.a_personal_stuff.app.provider.command;

import m.co.rh.id.a_personal_stuff.item_maintenance.provider.ItemMaintenanceCmdProviderModule;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.ItemReminderCmdProviderModule;
import m.co.rh.id.a_personal_stuff.item_usage.provider.ItemUsageCmdProviderModule;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class CommandProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(NewItemCmd.class, () -> new NewItemCmd(provider));
        providerRegistry.registerLazy(UpdateItemCmd.class, () -> new UpdateItemCmd(provider));
        providerRegistry.registerLazy(DeleteItemCmd.class, () -> new DeleteItemCmd(provider));
        providerRegistry.registerLazy(PagedItemCmd.class, () -> new PagedItemCmd(provider));
        providerRegistry.registerLazy(QueryItemCmd.class, () -> new QueryItemCmd(provider));
        providerRegistry.registerLazy(DeleteItemImageCmd.class, () -> new DeleteItemImageCmd(provider));
        providerRegistry.registerLazy(NewItemImageCmd.class, () -> new NewItemImageCmd(provider));
        providerRegistry.registerLazy(DeleteItemTagCmd.class, () -> new DeleteItemTagCmd(provider));
        providerRegistry.registerLazy(NewItemTagCmd.class, () -> new NewItemTagCmd(provider));
        providerRegistry.registerModule(new ItemUsageCmdProviderModule());
        providerRegistry.registerModule(new ItemMaintenanceCmdProviderModule());
        providerRegistry.registerModule(new ItemReminderCmdProviderModule());
    }

    @Override
    public void dispose(Provider provider) {
        // leave blank
    }
}
