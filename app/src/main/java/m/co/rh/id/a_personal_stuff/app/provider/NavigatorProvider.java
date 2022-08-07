package m.co.rh.id.a_personal_stuff.app.provider;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.MainActivity;
import m.co.rh.id.a_personal_stuff.app.ui.page.DonationsPage;
import m.co.rh.id.a_personal_stuff.app.ui.page.ItemDetailPage;
import m.co.rh.id.a_personal_stuff.app.ui.page.ItemSelectPage;
import m.co.rh.id.a_personal_stuff.app.ui.page.ItemsPage;
import m.co.rh.id.a_personal_stuff.app.ui.page.SplashPage;
import m.co.rh.id.a_personal_stuff.barcode.ui.NavBarcodeConfig;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.ImageViewPage;
import m.co.rh.id.a_personal_stuff.item_maintenance.ui.page.ItemMaintenanceDetailPage;
import m.co.rh.id.a_personal_stuff.item_maintenance.ui.page.ItemMaintenancesPage;
import m.co.rh.id.a_personal_stuff.item_reminder.ui.page.ItemReminderDetailPage;
import m.co.rh.id.a_personal_stuff.item_reminder.ui.page.ItemRemindersPage;
import m.co.rh.id.a_personal_stuff.item_usage.ui.page.ItemUsageDetailPage;
import m.co.rh.id.a_personal_stuff.item_usage.ui.page.ItemUsagesPage;
import m.co.rh.id.a_personal_stuff.settings.ui.page.SettingsPage;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

@SuppressWarnings("rawtypes")
public class NavigatorProvider implements ProviderDisposable {
    private Application mApplication;
    private Provider mProvider;
    private NavExtDialogConfig mNavExtDialogConfig;
    private NavBarcodeConfig mNavBarcodeConfig;
    private Map<Class<? extends Activity>, Navigator> mActivityNavigatorMap;

    public NavigatorProvider(Application application, Provider provider) {
        mApplication = application;
        mProvider = provider;
        mActivityNavigatorMap = new LinkedHashMap<>();
        mNavExtDialogConfig = mProvider.get(NavExtDialogConfig.class);
        mNavBarcodeConfig = mProvider.get(NavBarcodeConfig.class);
        setupMainActivityNavigator();
    }

    public INavigator getNavigator(Activity activity) {
        return mActivityNavigatorMap.get(activity.getClass());
    }

    @SuppressLint("InflateParams")
    @SuppressWarnings("unchecked")
    private Navigator setupMainActivityNavigator() {
        Map<String, StatefulViewFactory> navMap = new HashMap<>();
        navMap.put(Routes.HOME_PAGE, (args, activity) -> {
            if (args instanceof StatefulView) {
                return (StatefulView) args;
            }
            return new SplashPage();
        });
        navMap.put(Routes.SETTINGS_PAGE, (args, activity) -> new SettingsPage());
        navMap.put(Routes.DONATIONS_PAGE, (args, activity) -> new DonationsPage());
        navMap.put(Routes.ITEMS_PAGE, (args, activity) -> new ItemsPage());
        navMap.put(Routes.ITEM_DETAIL_PAGE, (args, activity) -> new ItemDetailPage());
        navMap.put(Routes.ITEM_SELECT_PAGE, (args, activity) -> new ItemSelectPage());
        navMap.put(Routes.ITEM_USAGES_PAGE, (args, activity) -> new ItemUsagesPage());
        navMap.put(Routes.ITEM_USAGE_DETAIL_PAGE, (args, activity) -> new ItemUsageDetailPage());
        navMap.put(Routes.ITEM_MAINTENANCES_PAGE, (args, activity) -> new ItemMaintenancesPage());
        navMap.put(Routes.ITEM_MAINTENANCE_DETAIL_PAGE, (args, activity) -> new ItemMaintenanceDetailPage());
        navMap.put(Routes.ITEM_REMINDERS_PAGE, (args, activity) -> new ItemRemindersPage());
        navMap.put(Routes.ITEM_REMINDER_DETAIL_PAGE, (args, activity) -> new ItemReminderDetailPage());
        navMap.put(Routes.COMMON_IMAGEVIEW, (args, activity) -> new ImageViewPage());
        navMap.putAll(mNavExtDialogConfig.getNavMap());
        navMap.putAll(mNavBarcodeConfig.getNavMap());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder(Routes.HOME_PAGE, navMap);
        navBuilder.setRequiredComponent(mProvider);
        navBuilder.setMainHandler(mProvider.get(Handler.class));
        navBuilder.setLoadingView(LayoutInflater.from(mProvider.getContext())
                .inflate(R.layout.page_splash, null));
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        mActivityNavigatorMap.put(MainActivity.class, navigator);
        mApplication.registerActivityLifecycleCallbacks(navigator);
        mApplication.registerComponentCallbacks(navigator);
        return navigator;
    }

    @Override
    public void dispose(Context context) {
        if (mActivityNavigatorMap != null && !mActivityNavigatorMap.isEmpty()) {
            for (Map.Entry<Class<? extends Activity>, Navigator> navEntry : mActivityNavigatorMap.entrySet()) {
                Navigator navigator = navEntry.getValue();
                mApplication.unregisterActivityLifecycleCallbacks(navigator);
                mApplication.unregisterComponentCallbacks(navigator);
            }
            mActivityNavigatorMap.clear();
        }
        mActivityNavigatorMap = null;
        mProvider = null;
        mApplication = null;
    }
}
