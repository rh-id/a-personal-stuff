package m.co.rh.id.a_personal_stuff.item_lending.provider;

import m.co.rh.id.a_personal_stuff.item_lending.provider.command.DeleteItemLendingCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.DeleteItemLendingImageCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.NewItemLendingCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.NewItemLendingImageCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.PagedItemLendingCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.QueryItemLendingCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.UpdateItemLendingCmd;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemLendingCmdProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(NewItemLendingCmd.class, () -> new NewItemLendingCmd(provider));
        providerRegistry.registerLazy(UpdateItemLendingCmd.class, () -> new UpdateItemLendingCmd(provider));
        providerRegistry.registerLazy(DeleteItemLendingCmd.class, () -> new DeleteItemLendingCmd(provider));
        providerRegistry.registerLazy(PagedItemLendingCmd.class, () -> new PagedItemLendingCmd(provider));
        providerRegistry.registerLazy(NewItemLendingImageCmd.class, () -> new NewItemLendingImageCmd(provider));
        providerRegistry.registerLazy(QueryItemLendingCmd.class, () -> new QueryItemLendingCmd(provider));
        providerRegistry.registerLazy(DeleteItemLendingImageCmd.class, () -> new DeleteItemLendingImageCmd(provider));
    }
}
