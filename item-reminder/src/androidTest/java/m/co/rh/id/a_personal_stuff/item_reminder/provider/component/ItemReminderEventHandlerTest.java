package m.co.rh.id.a_personal_stuff.item_reminder.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.room.ItemReminderDatabase;
import m.co.rh.id.a_personal_stuff.item_reminder.workmanager.WorkManagerConstants;
import m.co.rh.id.a_personal_stuff.item_reminder.workmanager.worker.ItemReminderNotificationWorker;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

@RunWith(AndroidJUnit4.class)
public class ItemReminderEventHandlerTest {

    private static final String DELETED_TASK_ID = "handler-test-deleted-task";

    private Context mContext;
    private ItemReminderDatabase mDb;
    private ExecutorService mExecutorService;
    private ItemChangeNotifier mNotifier;
    private WorkManager mWorkManager;
    private Provider mProvider;
    private ItemReminderEventHandler mHandler;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(mContext, ItemReminderDatabase.class)
                .allowMainThreadQueries().build();
        mExecutorService = Executors.newSingleThreadExecutor();
        mNotifier = new ItemChangeNotifier();
        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(mContext);
        } catch (IllegalStateException ignored) {
        }
        mWorkManager = WorkManager.getInstance(mContext);
        mProvider = Provider.createProvider(mContext, new TestProviderModule());
        mHandler = new ItemReminderEventHandler(mProvider);
    }

    @After
    public void tearDown() {
        if (mHandler != null) {
            mHandler.dispose(mContext);
        }
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
        }
        if (mDb != null) {
            mDb.close();
        }
        // best-effort shared WorkManager reset (process-shared instance)
        try {
            mWorkManager.cancelAllWork();
        } catch (Exception ignored) {
        }
    }

    @Test
    public void deletedItemEventDeletesReminderRowsAndCancelsWork() throws Exception {
        long deletedItemId = 1L;
        long survivingItemId = 2L;
        Date now = new Date();

        ItemReminder deletedReminder = new ItemReminder();
        deletedReminder.itemId = deletedItemId;
        deletedReminder.taskId = DELETED_TASK_ID;
        deletedReminder.reminderDateTime = new Date(now.getTime() + 3600000);
        deletedReminder.message = "deleted reminder";
        mDb.itemReminderDao().insertItemReminder(deletedReminder);

        // unique work the handler must cancel when the row goes away; the
        // initial delay keeps it ENQUEUED so CANCELLED is deterministic
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ItemReminderNotificationWorker.class)
                .setInitialDelay(1, TimeUnit.HOURS)
                .setInputData(new Data.Builder()
                        .putLong(WorkManagerConstants.KEY_LONG_ITEM_REMINDER_ID, deletedReminder.id)
                        .build())
                .build();
        mWorkManager.enqueueUniqueWork(DELETED_TASK_ID, ExistingWorkPolicy.KEEP, request);

        ItemReminder survivingReminder = new ItemReminder();
        survivingReminder.itemId = survivingItemId;
        survivingReminder.taskId = "handler-test-surviving-task";
        survivingReminder.reminderDateTime = new Date(now.getTime() + 3600000);
        survivingReminder.message = "surviving reminder";
        mDb.itemReminderDao().insertItemReminder(survivingReminder);

        mNotifier.itemDeleted(deletedItemState(deletedItemId));

        // the handler processes the event on its executor thread, so poll
        assertTrue(await(() -> mDb.itemReminderDao().findItemReminderByItemId(deletedItemId).isEmpty()));
        // other items' rows survive untouched
        assertEquals(1, mDb.itemReminderDao().findItemReminderByItemId(survivingItemId).size());
        // the unique work of the deleted reminder is cancelled by the handler
        assertTrue(await(this::isDeletedTaskCancelled));
    }

    private boolean isDeletedTaskCancelled() {
        try {
            List<WorkInfo> workInfos = mWorkManager.getWorkInfosForUniqueWork(DELETED_TASK_ID).get();
            return !workInfos.isEmpty()
                    && workInfos.get(0).getState() == WorkInfo.State.CANCELLED;
        } catch (Exception e) {
            return false;
        }
    }

    private ItemState deletedItemState(long itemId) {
        Item item = new Item();
        item.id = itemId;
        item.name = "deleted item " + itemId;
        ItemState itemState = new ItemState();
        itemState.updateItem(item);
        return itemState;
    }

    /**
     * Polls until the condition holds or the timeout elapses.
     */
    private boolean await(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && !condition.getAsBoolean()) {
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, this::getLogger);
            providerRegistry.register(ItemChangeNotifier.class, () -> mNotifier);
            providerRegistry.register(ItemReminderDao.class, () -> mDb.itemReminderDao());
            providerRegistry.register(WorkManager.class, () -> mWorkManager);
        }

        private ILogger getLogger() {
            return new ILogger() {
                @Override
                public void v(String tag, String message) {
                }

                @Override
                public void v(String tag, String message, Throwable throwable) {
                }

                @Override
                public void d(String tag, String message) {
                }

                @Override
                public void d(String tag, String message, Throwable throwable) {
                }

                @Override
                public void i(String tag, String message) {
                }

                @Override
                public void i(String tag, String message, Throwable throwable) {
                }

                @Override
                public void w(String tag, String message) {
                }

                @Override
                public void w(String tag, String message, Throwable throwable) {
                }

                @Override
                public void e(String tag, String message) {
                }

                @Override
                public void e(String tag, String message, Throwable throwable) {
                }

                @Override
                public void setLogLevel(int logLevel) {
                }
            };
        }
    }
}
