package m.co.rh.id.a_personal_stuff.item_reminder.provider;

import m.co.rh.id.a_personal_stuff.item_reminder.provider.component.ItemReminderEventHandler;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.notifier.ItemReminderChangeNotifier;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class ItemReminderProviderModule implements ProviderModule {
    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new ItemReminderDatabaseProviderModule());
        providerRegistry.registerAsync(ItemReminderEventHandler.class,
                () -> new ItemReminderEventHandler(provider));
        providerRegistry.registerLazy(ItemReminderChangeNotifier.class, ItemReminderChangeNotifier::new);
    }
}
