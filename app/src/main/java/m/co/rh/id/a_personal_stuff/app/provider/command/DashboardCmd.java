package m.co.rh.id.a_personal_stuff.app.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.app.provider.component.InventoryStatsCalculator;
import m.co.rh.id.a_personal_stuff.app.ui.model.DashboardData;
import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleDashboardContributor;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

public class DashboardCmd implements ProviderDisposable {

    private static final String TAG = DashboardCmd.class.getName();

    /**
     * Delays the refresh triggered by bursts of change events (e.g. an import
     * emits one event per item) so the dashboard recomputes only once.
     */
    private static final long REFRESH_DEBOUNCE_MILLIS = 700;
    private static final int SAMPLE_LIMIT = 3;

    private ExecutorService mExecutorService;
    private InventoryStatsCalculator mInventoryStatsCalculator;
    private ItemReminderDao mItemReminderDao;
    private SettingsSharedPreferences mSettingsSharedPreferences;
    private ItemChangeNotifier mItemChangeNotifier;
    private BackupContributorRegistry mContributorRegistry;
    private ILogger mLogger;
    private CompositeDisposable mCompositeDisposable;
    private final BehaviorSubject<DashboardData> mDashboardSubject;
    private final Subject<DashboardData> mDashboardEmitter;

    public DashboardCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mInventoryStatsCalculator = provider.get(InventoryStatsCalculator.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mSettingsSharedPreferences = provider.get(SettingsSharedPreferences.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
        mContributorRegistry = provider.get(BackupContributorRegistry.class);
        mLogger = provider.get(ILogger.class);
        mDashboardSubject = BehaviorSubject.createDefault(new DashboardData());
        mDashboardEmitter = mDashboardSubject.toSerialized();
        mCompositeDisposable = new CompositeDisposable();
        init();
        refresh();
    }

    private void init() {
        mCompositeDisposable.add(refreshTrigger()
                .debounce(REFRESH_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS)
                .subscribe(ignored -> refresh(),
                        throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)));
    }

    private Flowable<Object> refreshTrigger() {
        // base-DB item changes plus the per-module refresh flows contributed
        // through the BackupContributorRegistry (usage/purchase any-change,
        // reminder add/update/delete)
        List<Flowable<Object>> triggers = new ArrayList<>();
        triggers.add(mItemChangeNotifier.getAnyItemChangeFlow().map(itemState -> (Object) itemState));
        for (ModuleDashboardContributor contributor : mContributorRegistry.getDashboardContributors()) {
            triggers.addAll(contributor.getRefreshTriggers());
        }
        triggers.add(mSettingsSharedPreferences.getExpiryAlertLeadDaysFlow()
                .map(leadDays -> (Object) leadDays));
        return Flowable.merge(triggers);
    }

    public synchronized void refresh() {
        mExecutorService.execute(() -> {
            try {
                DashboardData dashboardData = new DashboardData();
                Date now = new Date();
                int leadDays = mSettingsSharedPreferences.getExpiryAlertLeadDays();
                InventoryStatsCalculator.AlertSummary alertSummary =
                        mInventoryStatsCalculator.compute(now, leadDays);
                dashboardData.totalItems = alertSummary.totalItems;
                dashboardData.inventoryValue = alertSummary.inventoryValue;
                dashboardData.expiredCount = alertSummary.expiredCount;
                dashboardData.expiringCount = alertSummary.expiringCount;
                // expiringItems has an open lower bound (includes expired);
                // partition it so each count's samples show only that
                // count's own items
                List<Item> expiredItems = new ArrayList<>();
                List<Item> futureExpiringItems = new ArrayList<>();
                for (Item item : alertSummary.expiringItems) {
                    if (item.expiredDateTime.after(now)) {
                        futureExpiringItems.add(item);
                    } else {
                        expiredItems.add(item);
                    }
                }
                dashboardData.expiredSamples = expiredItems.subList(0,
                        Math.min(SAMPLE_LIMIT, expiredItems.size()));
                dashboardData.expiringSamples = futureExpiringItems.subList(0,
                        Math.min(SAMPLE_LIMIT, futureExpiringItems.size()));
                dashboardData.lowStockCount = alertSummary.lowStockItems.size();
                dashboardData.lowStockSamples = alertSummary.lowStockItems.subList(0,
                        Math.min(SAMPLE_LIMIT, alertSummary.lowStockItems.size()));
                // Upcoming-reminder CONTENT stays :app-owned (not via the
                // contributor registry): it is the alert-digest payload shape
                // the :app notification renderer consumes, and the reminder
                // DAO queries are already module-agnostic SELECTs.
                dashboardData.upcomingReminders = mItemReminderDao.findNextReminders(now, SAMPLE_LIMIT);
                dashboardData.upcomingReminderCount = mItemReminderDao.countUpcomingReminders(now);
                mDashboardEmitter.onNext(dashboardData);
            } catch (Throwable throwable) {
                mLogger.e(TAG, throwable.getMessage(), throwable);
            }
        });
    }

    public Flowable<DashboardData> getDashboardFlow() {
        return Flowable.fromObservable(mDashboardEmitter, BackpressureStrategy.BUFFER);
    }

    @Override
    public void dispose(Context context) {
        mCompositeDisposable.dispose();
    }
}
