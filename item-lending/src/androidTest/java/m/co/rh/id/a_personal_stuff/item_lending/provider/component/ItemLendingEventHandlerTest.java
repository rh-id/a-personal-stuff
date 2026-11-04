package m.co.rh.id.a_personal_stuff.item_lending.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.base.test.AndroidTestUtils;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLending;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLendingImage;
import m.co.rh.id.a_personal_stuff.item_lending.room.ItemLendingDatabase;
import m.co.rh.id.a_personal_stuff.item_lending.workmanager.WorkManagerConstants;
import m.co.rh.id.a_personal_stuff.item_lending.workmanager.worker.ItemLendingReturnNotificationWorker;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

@RunWith(AndroidJUnit4.class)
public class ItemLendingEventHandlerTest {

    private static final String DELETED_TASK_ID = "handler-test-deleted-task";

    private Context mContext;
    private ItemLendingDatabase mDb;
    private ExecutorService mExecutorService;
    private ItemChangeNotifier mNotifier;
    private WorkManager mWorkManager;
    private Provider mProvider;
    private ItemLendingEventHandler mHandler;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(mContext, ItemLendingDatabase.class)
                .allowMainThreadQueries().build();
        mExecutorService = Executors.newSingleThreadExecutor();
        mNotifier = new ItemChangeNotifier();
        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(mContext);
        } catch (IllegalStateException ignored) {
        }
        mWorkManager = WorkManager.getInstance(mContext);
        mProvider = Provider.createProvider(mContext, new TestProviderModule());
        mHandler = new ItemLendingEventHandler(mProvider);
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
    public void deletedItemEventDeletesLendingRowsImagesAndCancelsWork() throws Exception {
        long deletedItemId = 1L;
        long survivingItemId = 2L;
        Date now = new Date();

        ItemLending deletedLending = new ItemLending();
        deletedLending.itemId = deletedItemId;
        deletedLending.taskId = DELETED_TASK_ID;
        deletedLending.borrowerName = "deleted borrower";
        deletedLending.lendDateTime = now;
        deletedLending.dueDateTime = new Date(now.getTime() + 3600000);
        long deletedLendingId = mDb.itemLendingDao().insert(deletedLending);

        ItemLendingImage deletedImage = new ItemLendingImage();
        deletedImage.itemLendingId = deletedLendingId;
        deletedImage.fileName = "deleted_lending.jpg";
        deletedImage.createdDateTime = now;
        mDb.itemLendingDao().insert(deletedImage);

        // unique work the handler must cancel when the row goes away; the
        // initial delay keeps it ENQUEUED so CANCELLED is deterministic
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ItemLendingReturnNotificationWorker.class)
                .setInitialDelay(1, TimeUnit.HOURS)
                .setInputData(new Data.Builder()
                        .putLong(WorkManagerConstants.KEY_LONG_ITEM_LENDING_ID, deletedLendingId)
                        .build())
                .build();
        mWorkManager.enqueueUniqueWork(DELETED_TASK_ID, ExistingWorkPolicy.KEEP, request);

        ItemLending survivingLending = new ItemLending();
        survivingLending.itemId = survivingItemId;
        survivingLending.taskId = "handler-test-surviving-task";
        survivingLending.borrowerName = "surviving borrower";
        survivingLending.lendDateTime = now;
        survivingLending.dueDateTime = new Date(now.getTime() + 3600000);
        long survivingLendingId = mDb.itemLendingDao().insert(survivingLending);

        ItemLendingImage survivingImage = new ItemLendingImage();
        survivingImage.itemLendingId = survivingLendingId;
        survivingImage.fileName = "surviving_lending.jpg";
        survivingImage.createdDateTime = now;
        mDb.itemLendingDao().insert(survivingImage);

        mNotifier.itemDeleted(deletedItemState(deletedItemId));

        // the handler processes the event on its executor thread, so poll
        assertTrue(AndroidTestUtils.await(() -> mDb.itemLendingDao().findItemLendingByItemId(deletedItemId).isEmpty()));
        // the second-level image row must be gone with its parent
        assertNull(mDb.itemLendingDao().findItemLendingImageByFileName("deleted_lending.jpg"));
        // other items' rows survive untouched
        assertEquals(1, mDb.itemLendingDao().findItemLendingByItemId(survivingItemId).size());
        assertNotNull(mDb.itemLendingDao().findItemLendingImageByFileName("surviving_lending.jpg"));
        // the unique work of the deleted lending is cancelled by the handler
        assertTrue(AndroidTestUtils.await(this::isDeletedTaskCancelled));
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

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemChangeNotifier.class, () -> mNotifier);
            providerRegistry.register(ItemLendingDao.class, () -> mDb.itemLendingDao());
            providerRegistry.register(WorkManager.class, () -> mWorkManager);
        }
    }
}
