package m.co.rh.id.a_personal_stuff.item_usage.provider;

import m.co.rh.id.a_personal_stuff.item_usage.provider.command.DeleteItemUsageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.DeleteItemUsageImageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.NewItemUsageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.NewItemUsageImageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.PagedItemUsageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.QueryItemUsageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.UpdateItemUsageCmd;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemUsageCmdProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(NewItemUsageCmd.class, () -> new NewItemUsageCmd(provider));
        providerRegistry.registerLazy(UpdateItemUsageCmd.class, () -> new UpdateItemUsageCmd(provider));
        providerRegistry.registerLazy(DeleteItemUsageCmd.class, () -> new DeleteItemUsageCmd(provider));
        providerRegistry.registerLazy(PagedItemUsageCmd.class, () -> new PagedItemUsageCmd(provider));
        providerRegistry.registerLazy(NewItemUsageImageCmd.class, () -> new NewItemUsageImageCmd(provider));
        providerRegistry.registerLazy(QueryItemUsageCmd.class, () -> new QueryItemUsageCmd(provider));
        providerRegistry.registerLazy(DeleteItemUsageImageCmd.class, () -> new DeleteItemUsageImageCmd(provider));
    }

    @Override
    public void dispose(Provider provider) {
        // Leave blank
    }
}
