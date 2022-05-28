package m.co.rh.id.a_personal_stuff.app.provider;

import android.app.Application;

import m.co.rh.id.a_personal_stuff.app.provider.command.CommandProviderModule;
import m.co.rh.id.a_personal_stuff.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_personal_stuff.app.provider.component.BuildConfigInfo;
import m.co.rh.id.a_personal_stuff.barcode.provider.BarcodeProviderModule;
import m.co.rh.id.a_personal_stuff.base.provider.BaseProviderModule;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.provider.RxProviderModule;
import m.co.rh.id.a_personal_stuff.base.provider.component.IBuildConfigInfo;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.ItemMaintenanceProviderModule;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.ItemReminderProviderModule;
import m.co.rh.id.a_personal_stuff.item_usage.provider.ItemUsageProviderModule;
import m.co.rh.id.a_personal_stuff.settings.provider.SettingsProviderModule;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class AppProviderModule implements ProviderModule {

    private Application mApplication;

    public AppProviderModule(Application application) {
        mApplication = application;
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerModule(new CommandProviderModule());
        providerRegistry.registerModule(new RxProviderModule());
        providerRegistry.registerModule(new BarcodeProviderModule());
        providerRegistry.registerModule(new ItemUsageProviderModule());
        providerRegistry.registerModule(new ItemMaintenanceProviderModule());
        providerRegistry.registerModule(new ItemReminderProviderModule());
        providerRegistry.registerModule(new SettingsProviderModule());

        providerRegistry.registerLazy(IBuildConfigInfo.class, BuildConfigInfo::new);
        providerRegistry.registerLazy(AppNotificationHandler.class, () -> new AppNotificationHandler(provider));

        providerRegistry.registerPool(IStatefulViewProvider.class, () -> new StatefulViewProvider(provider));
        // it is safer to register navigator last in case it needs dependency from all above, provider can be passed here
        providerRegistry.register(NavigatorProvider.class, new NavigatorProvider(mApplication, provider));
    }
}
