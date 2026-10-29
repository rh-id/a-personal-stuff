package m.co.rh.id.a_personal_stuff.item_checklist.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.room.ItemChecklistDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

@RunWith(AndroidJUnit4.class)
public class ItemChecklistEventHandlerTest {

    private Context mContext;
    private ItemChecklistDatabase mDb;
    private ExecutorService mExecutorService;
    private ItemChangeNotifier mNotifier;
    private Provider mProvider;
    private ItemChecklistEventHandler mHandler;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(mContext, ItemChecklistDatabase.class)
                .allowMainThreadQueries().build();
        mExecutorService = Executors.newSingleThreadExecutor();
        mNotifier = new ItemChangeNotifier();
        mProvider = Provider.createProvider(mContext, new TestProviderModule());
        mHandler = new ItemChecklistEventHandler(mProvider);
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
    }

    @Test
    public void deletedItemEventDeletesOnlyItsChecklistItemRows() throws Exception {
        long deletedItemId = 1L;
        long survivingItemId = 2L;
        Date now = new Date();

        ItemChecklist checklist = new ItemChecklist();
        checklist.title = "Handler checklist";
        checklist.createdDateTime = now;
        checklist.updatedDateTime = now;
        ItemChecklistState checklistState = new ItemChecklistState();
        checklistState.updateItemChecklist(checklist);
        mDb.itemChecklistDao().insertItemChecklist(checklistState);
        long checklistId = checklistState.getChecklistId();

        ItemChecklistItem deletedItemRow = new ItemChecklistItem();
        deletedItemRow.itemChecklistId = checklistId;
        deletedItemRow.itemId = deletedItemId;
        deletedItemRow.createdDateTime = now;
        mDb.itemChecklistDao().insertItemChecklistItems(Collections.singletonList(deletedItemRow));

        ItemChecklistItem survivingItemRow = new ItemChecklistItem();
        survivingItemRow.itemChecklistId = checklistId;
        survivingItemRow.itemId = survivingItemId;
        survivingItemRow.createdDateTime = now;
        mDb.itemChecklistDao().insertItemChecklistItems(Collections.singletonList(survivingItemRow));

        mNotifier.itemDeleted(deletedItemState(deletedItemId));

        // the handler processes the event on its executor thread, so poll
        assertTrue(await(() -> !mDb.itemChecklistDao()
                .findExistingItemIds(checklistId).contains(deletedItemId)));
        // other items' checklist rows survive untouched
        List<Long> remainingItemIds = mDb.itemChecklistDao().findExistingItemIds(checklistId);
        assertEquals(Collections.singletonList(survivingItemId), remainingItemIds);
        // the handler only deletes item_checklist_item rows: the parent
        // item_checklist row has no item_id and intentionally survives
        assertEquals(1, mDb.itemChecklistDao().findAllItemChecklists().size());
        assertFalse(mDb.itemChecklistDao().findAllItemChecklistItems().isEmpty());
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
            providerRegistry.register(ItemChecklistDao.class, () -> mDb.itemChecklistDao());
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
