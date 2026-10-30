package m.co.rh.id.a_personal_stuff.item_reminder.provider;

import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.component.ItemReminderBackupContributor;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.component.ItemReminderDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.component.ItemReminderEventHandler;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.component.ItemReminderSpreadsheetContributor;
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
        // Synchronous contributor registration (never registerAsync): the
        // registry is eager-registered by BaseProviderModule, and :app
        // iterates the contributors after all modules have provided().
        BackupContributorRegistry contributorRegistry = provider.get(BackupContributorRegistry.class);
        contributorRegistry.register(new ItemReminderBackupContributor(provider));
        contributorRegistry.register(new ItemReminderSpreadsheetContributor(provider));
        contributorRegistry.register(new ItemReminderDashboardContributor(provider));
    }
}
