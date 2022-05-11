package m.co.rh.id.a_personal_stuff.item_reminder.provider;

import m.co.rh.id.a_personal_stuff.item_reminder.provider.command.DeleteItemReminderCmd;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.command.NewItemReminderCmd;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.command.PagedItemReminderCmd;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.command.QueryItemReminderCmd;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemReminderCmdProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(NewItemReminderCmd.class, () -> new NewItemReminderCmd(provider));
        providerRegistry.registerLazy(DeleteItemReminderCmd.class, () -> new DeleteItemReminderCmd(provider));
        providerRegistry.registerLazy(QueryItemReminderCmd.class, () -> new QueryItemReminderCmd(provider));
        providerRegistry.registerLazy(PagedItemReminderCmd.class, () -> new PagedItemReminderCmd(provider));
    }

    @Override
    public void dispose(Provider provider) {
        // Leave blank
    }
}
