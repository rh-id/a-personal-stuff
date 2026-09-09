package m.co.rh.id.a_personal_stuff.app.provider.component;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_personal_stuff.app.workmanager.WorkManagerConstants;
import m.co.rh.id.a_personal_stuff.app.workmanager.worker.AlertDigestWorker;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Enqueues the daily inventory alert digest unique periodic work. Scheduling
 * is done in the constructor (async factory side effect, mirroring the
 * event-handler constructor subscriptions) so the digest is (re)scheduled on
 * every app start.
 */
public class InventoryAlertScheduler {

    private static final String TAG = InventoryAlertScheduler.class.getName();

    private WorkManager mWorkManager;
    private ILogger mLogger;

    public InventoryAlertScheduler(Provider provider) {
        mWorkManager = provider.get(WorkManager.class);
        mLogger = provider.get(ILogger.class);
        schedule();
    }

    private void schedule() {
        try {
            // UPDATE keeps the original daily cycle on later app starts (no
            // reset), while the 1-minute initial delay only applies on the
            // very first enrollment, giving a quick first digest.
            mWorkManager.enqueueUniquePeriodicWork(
                    WorkManagerConstants.UNIQUE_WORK_INVENTORY_ALERT_DIGEST,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    new PeriodicWorkRequest.Builder(AlertDigestWorker.class, 1, TimeUnit.DAYS)
                            .setInitialDelay(1, TimeUnit.MINUTES)
                            .build());
        } catch (Throwable throwable) {
            mLogger.e(TAG, throwable.getMessage(), throwable);
        }
    }
}
