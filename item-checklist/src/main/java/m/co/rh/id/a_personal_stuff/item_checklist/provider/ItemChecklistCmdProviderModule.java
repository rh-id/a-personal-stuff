package m.co.rh.id.a_personal_stuff.item_checklist.provider;

import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.AddItemChecklistItemCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.DeleteItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.DeleteItemChecklistItemCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.NewItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.PagedItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.QueryItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.UpdateItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.UpdateItemChecklistItemCmd;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemChecklistCmdProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(NewItemChecklistCmd.class, () -> new NewItemChecklistCmd(provider));
        providerRegistry.registerLazy(UpdateItemChecklistCmd.class, () -> new UpdateItemChecklistCmd(provider));
        providerRegistry.registerLazy(DeleteItemChecklistCmd.class, () -> new DeleteItemChecklistCmd(provider));
        providerRegistry.registerLazy(PagedItemChecklistCmd.class, () -> new PagedItemChecklistCmd(provider));
        providerRegistry.registerLazy(QueryItemChecklistCmd.class, () -> new QueryItemChecklistCmd(provider));
        providerRegistry.registerLazy(AddItemChecklistItemCmd.class, () -> new AddItemChecklistItemCmd(provider));
        providerRegistry.registerLazy(UpdateItemChecklistItemCmd.class, () -> new UpdateItemChecklistItemCmd(provider));
        providerRegistry.registerLazy(DeleteItemChecklistItemCmd.class, () -> new DeleteItemChecklistItemCmd(provider));
    }
}
