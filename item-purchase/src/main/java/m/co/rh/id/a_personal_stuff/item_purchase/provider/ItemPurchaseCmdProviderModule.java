package m.co.rh.id.a_personal_stuff.item_purchase.provider;

import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.DeleteItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.DeleteItemPurchaseImageCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.NewItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.NewItemPurchaseImageCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.PagedItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.QueryItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.UpdateItemPurchaseCmd;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemPurchaseCmdProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(NewItemPurchaseCmd.class, () -> new NewItemPurchaseCmd(provider));
        providerRegistry.registerLazy(UpdateItemPurchaseCmd.class, () -> new UpdateItemPurchaseCmd(provider));
        providerRegistry.registerLazy(DeleteItemPurchaseCmd.class, () -> new DeleteItemPurchaseCmd(provider));
        providerRegistry.registerLazy(PagedItemPurchaseCmd.class, () -> new PagedItemPurchaseCmd(provider));
        providerRegistry.registerLazy(NewItemPurchaseImageCmd.class, () -> new NewItemPurchaseImageCmd(provider));
        providerRegistry.registerLazy(QueryItemPurchaseCmd.class, () -> new QueryItemPurchaseCmd(provider));
        providerRegistry.registerLazy(DeleteItemPurchaseImageCmd.class, () -> new DeleteItemPurchaseImageCmd(provider));
    }
}
