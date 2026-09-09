package m.co.rh.id.a_personal_stuff.app.workmanager.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Date;

import m.co.rh.id.a_personal_stuff.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_personal_stuff.app.provider.component.InventoryStatsCalculator;
import m.co.rh.id.a_personal_stuff.base.BaseApplication;
import m.co.rh.id.a_personal_stuff.base.util.NotificationPermissionHelper;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class AlertDigestWorker extends Worker {

    private static final String TAG = AlertDigestWorker.class.getName();

    public AlertDigestWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
            SettingsSharedPreferences settings = provider.get(SettingsSharedPreferences.class);
            boolean expiryEnabled = settings.isExpiryAlertEnabled();
            boolean lowStockEnabled = settings.isLowStockAlertEnabled();
            if (!expiryEnabled && !lowStockEnabled) {
                return Result.success();
            }
            InventoryStatsCalculator inventoryStatsCalculator = provider.get(InventoryStatsCalculator.class);
            Date now = new Date();
            InventoryStatsCalculator.AlertSummary alertSummary =
                    inventoryStatsCalculator.compute(now, settings.getExpiryAlertLeadDays());
            boolean hasExpiryAlert = expiryEnabled
                    && (alertSummary.expiredCount > 0 || alertSummary.expiringCount > 0);
            boolean hasLowStockAlert = lowStockEnabled && !alertSummary.lowStockItems.isEmpty();
            if (!hasExpiryAlert && !hasLowStockAlert) {
                return Result.success();
            }
            if (!NotificationPermissionHelper.canPostNotifications(getApplicationContext())) {
                return Result.success();
            }
            AppNotificationHandler notificationHandler = provider.get(AppNotificationHandler.class);
            notificationHandler.postInventoryDigestNotification(expiryEnabled, lowStockEnabled, alertSummary, now);
            return Result.success();
        } catch (Exception e) {
            try {
                BaseApplication.of(getApplicationContext()).getProvider()
                        .get(ILogger.class).e(TAG, e.getMessage(), e);
            } catch (Throwable ignored) {
            }
            return Result.failure();
        }
    }
}
