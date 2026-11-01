package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.observers.TestObserver;
import m.co.rh.id.a_personal_stuff.base.test.DirectExecutorService;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.room.ItemChecklistDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Component tests for {@link QueryItemChecklistCmd} against a REAL
 * in-memory ItemChecklistDatabase: {@link QueryItemChecklistCmd#existsById}
 * — the cheap existence check that keeps the checklist-list page's
 * designated shopping-list badge off dangling ids (deleted checklists) —
 * and {@link QueryItemChecklistCmd#findItemChecklistStateById}, whose Maybe
 * completes empty for a stale id so stale-id consumers skip cleanly instead
 * of erroring.
 */
@RunWith(AndroidJUnit4.class)
public class QueryItemChecklistCmdTest {

    private Context mContext;
    private ItemChecklistDatabase mDb;
    private ExecutorService mExecutorService;
    private Provider mProvider;
    private ItemChecklistDao mItemChecklistDao;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(mContext, ItemChecklistDatabase.class)
                .allowMainThreadQueries().build();
        mItemChecklistDao = mDb.itemChecklistDao();
        mExecutorService = new DirectExecutorService();
        mProvider = Provider.createProvider(mContext, new TestProviderModule());
    }

    @After
    public void tearDown() {
        if (mDb != null) {
            mDb.close();
        }
    }

    @Test
    public void existsByIdTrueWhenChecklistExists() {
        ItemChecklist checklist = new ItemChecklist();
        checklist.title = "Groceries";
        checklist.createdDateTime = new Date(0L);
        checklist.updatedDateTime = new Date(0L);
        ItemChecklistState state = new ItemChecklistState();
        state.updateItemChecklist(checklist);
        mItemChecklistDao.insertItemChecklist(state);

        Boolean exists = new QueryItemChecklistCmd(mProvider)
                .existsById(checklist.id).blockingGet();

        assertTrue(exists);
    }

    @Test
    public void existsByIdFalseWhenChecklistDeleted() {
        // a dangling stored id must read as missing so the badge clears
        Boolean exists = new QueryItemChecklistCmd(mProvider).existsById(7L).blockingGet();

        assertFalse(exists);
    }

    @Test
    public void findItemChecklistStateByIdCompletesEmptyWhenChecklistDeleted() {
        ItemChecklist checklist = new ItemChecklist();
        checklist.title = "Doomed checklist";
        checklist.createdDateTime = new Date(0L);
        checklist.updatedDateTime = new Date(0L);
        ItemChecklistState state = new ItemChecklistState();
        state.updateItemChecklist(checklist);
        mItemChecklistDao.insertItemChecklist(state);
        long checklistId = checklist.id;
        // sanity: the state is present BEFORE the deletion
        assertNotNull(new QueryItemChecklistCmd(mProvider)
                .findItemChecklistStateById(checklistId).blockingGet());

        // delete ONLY the parent row (item_checklist_item has no FK
        // constraint on item_checklist), same pattern as the
        // ItemChecklistServiceTest missing-parent guard tests
        mDb.getOpenHelper().getWritableDatabase()
                .execSQL("DELETE FROM item_checklist WHERE id = ?",
                        new Object[]{checklistId});

        TestObserver<ItemChecklistState> observer = new QueryItemChecklistCmd(mProvider)
                .findItemChecklistStateById(checklistId).test();

        // the stale id completes EMPTY (consumers skip it) instead of
        // erroring the stream
        observer.assertComplete();
        observer.assertNoValues();
    }

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            // the direct executor keeps the Single's subscribeOn path inline
            // on the calling thread, so blockingGet is deterministic
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemChecklistDao.class, () -> mItemChecklistDao);
        }
    }
}
