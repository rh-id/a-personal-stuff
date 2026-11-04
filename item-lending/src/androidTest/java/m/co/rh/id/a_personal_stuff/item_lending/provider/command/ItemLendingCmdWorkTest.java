package m.co.rh.id.a_personal_stuff.item_lending.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
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

import m.co.rh.id.a_personal_stuff.base.test.AndroidTestUtils;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.a_personal_stuff.item_lending.provider.notifier.ItemLendingChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_lending.room.ItemLendingDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

@RunWith(AndroidJUnit4.class)
public class ItemLendingCmdWorkTest {

    private Context mContext;
    private ItemLendingDatabase mDb;
    private ExecutorService mExecutorService;
    private WorkManager mWorkManager;
    private Provider mProvider;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(mContext, ItemLendingDatabase.class)
                .allowMainThreadQueries().build();
        mExecutorService = Executors.newSingleThreadExecutor();
        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(mContext);
        } catch (IllegalStateException ignored) {
        }
        mWorkManager = WorkManager.getInstance(mContext);
        mProvider = Provider.createProvider(mContext, new TestProviderModule());
    }

    @After
    public void tearDown() {
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
    public void newFutureDueLendingSchedulesUniqueWork() throws Exception {
        ItemLendingState lendingState = futureDueLendingState();
        new NewItemLendingCmd(mProvider).execute(lendingState).blockingGet();

        List<WorkInfo> workInfos = workInfosFor(lendingState.getItemLending().taskId);
        assertEquals(1, workInfos.size());
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.get(0).getState());
    }

    @Test
    public void markReturnedCancelsUniqueWork() throws Exception {
        ItemLendingState lendingState = futureDueLendingState();
        String taskId = lendingState.getItemLending().taskId;
        new NewItemLendingCmd(mProvider).execute(lendingState).blockingGet();
        assertEquals(1, workInfosFor(taskId).size());

        // marking the lending returned must cancel the pending return reminder
        lendingState.updateReturnedDateTime(new Date());
        new UpdateItemLendingCmd(mProvider).execute(lendingState).blockingGet();

        assertTrue(AndroidTestUtils.await(() -> isTaskCancelled(taskId)));
    }

    @Test
    public void deleteCancelsUniqueWork() throws Exception {
        ItemLendingState lendingState = futureDueLendingState();
        String taskId = lendingState.getItemLending().taskId;
        new NewItemLendingCmd(mProvider).execute(lendingState).blockingGet();
        assertEquals(1, workInfosFor(taskId).size());

        new DeleteItemLendingCmd(mProvider).execute(lendingState).blockingGet();

        assertTrue(AndroidTestUtils.await(() -> isTaskCancelled(taskId)));
    }

    @Test
    public void pastDueLendingDoesNotScheduleWork() throws Exception {
        ItemLendingState lendingState = futureDueLendingState();
        // due in the past: no return reminder may be scheduled
        lendingState.updateDueDateTime(new Date(System.currentTimeMillis() - 3600000));
        new NewItemLendingCmd(mProvider).execute(lendingState).blockingGet();

        assertTrue(workInfosFor(lendingState.getItemLending().taskId).isEmpty());
    }

    private ItemLendingState futureDueLendingState() {
        Date now = new Date();
        ItemLendingState lendingState = new ItemLendingState();
        lendingState.setBorrowerName("cmd work borrower");
        lendingState.updateLendDateTime(now);
        lendingState.updateDueDateTime(new Date(now.getTime() + 3600000));
        return lendingState;
    }

    private List<WorkInfo> workInfosFor(String taskId) throws Exception {
        return mWorkManager.getWorkInfosForUniqueWork(taskId).get();
    }

    private boolean isTaskCancelled(String taskId) {
        try {
            List<WorkInfo> workInfos = mWorkManager.getWorkInfosForUniqueWork(taskId).get();
            return !workInfos.isEmpty()
                    && workInfos.get(0).getState() == WorkInfo.State.CANCELLED;
        } catch (Exception e) {
            return false;
        }
    }

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(WorkManager.class, () -> mWorkManager);
            providerRegistry.register(ItemLendingDao.class, () -> mDb.itemLendingDao());
            providerRegistry.register(ItemLendingChangeNotifier.class, ItemLendingChangeNotifier::new);
        }
    }
}
