package m.co.rh.id.a_personal_stuff.app.provider.component;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.app.workmanager.WorkManagerConstants;
import m.co.rh.id.a_personal_stuff.app.workmanager.worker.AutoBackupWorker;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Keeps the automatic backup unique periodic work in sync with the settings.
 * Scheduling is done in the constructor via a combineLatest subscription on
 * the enabled/interval/folder flows (mirroring the InventoryAlertScheduler
 * constructor side effect), so the schedule is refreshed on every app start
 * and on every settings change.
 */
public class AutoBackupScheduler {

    private static final String TAG = AutoBackupScheduler.class.getName();

    private final WorkManager mWorkManager;
    private final ILogger mLogger;
    private final SettingsSharedPreferences mSettingsSharedPreferences;
    private final RxDisposer mRxDisposer;

    public AutoBackupScheduler(Provider provider) {
        mWorkManager = provider.get(WorkManager.class);
        mLogger = provider.get(ILogger.class);
        mSettingsSharedPreferences = provider.get(SettingsSharedPreferences.class);
        mRxDisposer = provider.get(RxDisposer.class);
        mRxDisposer.add("AutoBackupScheduler_onAutoBackupSettingsChanged",
                Flowable.combineLatest(
                        mSettingsSharedPreferences.getAutoBackupEnabledFlow(),
                        mSettingsSharedPreferences.getAutoBackupIntervalDaysFlow(),
                        mSettingsSharedPreferences.getAutoBackupTreeUriFlow(),
                        BackupConfig::new)
                        .subscribe(this::onConfigChanged,
                                throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)));
    }

    private void onConfigChanged(BackupConfig config) {
        try {
            boolean run = config.enabled
                    && config.treeUri != null
                    && !config.treeUri.isEmpty();
            if (run) {
                // UPDATE keeps the original run cycle across app starts (no
                // reset); changing the interval updates the request spec
                // without restarting the next run time
                mWorkManager.enqueueUniquePeriodicWork(
                        WorkManagerConstants.UNIQUE_WORK_AUTO_BACKUP,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        new PeriodicWorkRequest.Builder(AutoBackupWorker.class,
                                config.intervalDays, TimeUnit.DAYS)
                                .setConstraints(new Constraints.Builder()
                                        .setRequiresBatteryNotLow(true)
                                        .build())
                                .build());
            } else {
                mWorkManager.cancelUniqueWork(WorkManagerConstants.UNIQUE_WORK_AUTO_BACKUP);
            }
        } catch (Throwable throwable) {
            mLogger.e(TAG, throwable.getMessage(), throwable);
        }
    }

    private static class BackupConfig {
        final boolean enabled;
        final int intervalDays;
        final String treeUri;

        BackupConfig(boolean enabled, int intervalDays, String treeUri) {
            this.enabled = enabled;
            this.intervalDays = intervalDays;
            this.treeUri = treeUri;
        }
    }
}
