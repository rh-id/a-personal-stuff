package m.co.rh.id.a_personal_stuff.item_checklist.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_personal_stuff.base.test.DirectExecutorService;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_checklist.room.ItemChecklistDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Component tests for the shared checklist mutation service against a REAL
 * in-memory ItemChecklistDatabase, the real {@link ItemChecklistChangeNotifier}
 * and the real {@link ItemChecklistService}: every checklist mutation
 * (create / update / delete of a checklist, add / check-off / uncheck /
 * delete of its items) that the checklist Cmds and the app-level
 * shopping-list orchestrator both delegate to. Notifier flows are subscribed
 * with {@code Flowable.test()} BEFORE the mutation, then asserted — all
 * service calls are synchronous, so the emissions are already buffered when
 * the call returns.
 */
@RunWith(AndroidJUnit4.class)
public class ItemChecklistServiceTest {

    private static final Date OLD_DATE = new Date(0L);

    private ItemChecklistDatabase mDb;
    private ItemChecklistDao mItemChecklistDao;
    private ItemChecklistChangeNotifier mItemChecklistChangeNotifier;
    private ExecutorService mExecutorService;
    private Provider mProvider;
    private ItemChecklistService mItemChecklistService;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(context, ItemChecklistDatabase.class)
                .allowMainThreadQueries().build();
        mItemChecklistDao = mDb.itemChecklistDao();
        mItemChecklistChangeNotifier = new ItemChecklistChangeNotifier();
        mExecutorService = new DirectExecutorService();
        mProvider = Provider.createProvider(context, new TestProviderModule());
        mItemChecklistService = new ItemChecklistService(mProvider);
    }

    @After
    public void tearDown() {
        if (mDb != null) {
            mDb.close();
        }
    }

    @Test
    public void createChecklistInsertsFiresAddedAndReturnsState() {
        TestSubscriber<ItemChecklistState> addedSubscriber =
                mItemChecklistChangeNotifier.getChecklistAddedFlow().test();
        ItemChecklistState state = new ItemChecklistState();
        state.setTitle("Groceries");

        ItemChecklistState returned = mItemChecklistService.createChecklist(state);

        // the same instance is returned so the caller sees the
        // Room-populated id (mirrors the DAO contract)
        assertSame(state, returned);
        assertTrue(returned.getChecklistId() > 0);
        addedSubscriber.awaitCount(1);
        addedSubscriber.assertValueCount(1);
        // the notifier receives a clone: subscribers must not be able to
        // mutate the caller's state through the event
        ItemChecklistState notified = addedSubscriber.values().get(0);
        assertNotSame(state, notified);
        assertNotSame(state.getItemChecklist(), notified.getItemChecklist());
        assertEquals("Groceries", notified.getTitle());
        assertEquals(state.getChecklistId(), notified.getChecklistId());
        // the row is really persisted
        assertEquals(1, mItemChecklistDao.findAllItemChecklists().size());
    }

    @Test
    public void addItemIdsSkipsExistingInsertsNewAndBumpsNotifies() {
        ItemChecklistState existing = insertChecklist("Shopping list");
        long checklistId = existing.getChecklistId();
        // item 1 is already on the checklist: only 2 and 3 must be inserted
        insertChecklistItemRow(checklistId, 1L, null);
        TestSubscriber<ItemChecklistState> updatedSubscriber =
                mItemChecklistChangeNotifier.getChecklistUpdatedFlow().test();

        int added = mItemChecklistService.addItemIds(checklistId,
                Arrays.asList(1L, 2L, 3L));

        assertEquals(2, added);
        // the rows are really persisted: item 1 skipped, 2 and 3 inserted
        // unchecked
        List<ItemChecklistItem> rows =
                mItemChecklistDao.findItemChecklistItemsByChecklistId(checklistId);
        assertEquals(3, rows.size());
        for (ItemChecklistItem row : rows) {
            assertEquals(checklistId, row.itemChecklistId.longValue());
            if (row.itemId != 1L) {
                assertTrue(row.itemId == 2L || row.itemId == 3L);
                assertNull(row.checkedDateTime);
            }
        }
        // the checklist row changed even though it was not inserted itself:
        // its timestamp is bumped and open detail pages are notified
        ItemChecklist checklist = mItemChecklistDao.findAllItemChecklists().get(0);
        assertTrue(checklist.updatedDateTime.after(OLD_DATE));
        updatedSubscriber.awaitCount(1);
        updatedSubscriber.assertValueCount(1);
        // the notifier state is the fresh re-read clone, not the caller's
        // mutated instance
        ItemChecklistState notified = updatedSubscriber.values().get(0);
        assertNotSame(existing, notified);
        assertEquals(checklistId, notified.getChecklistId().longValue());
        assertTrue(notified.getUpdatedDateTime().after(OLD_DATE));
        assertEquals(3, notified.getItemChecklistItems().size());
    }

    @Test
    public void addItemIdsWithNothingNewDoesNotTouchDaoOrNotifier() {
        ItemChecklistState existing = insertChecklist("Shopping list");
        long checklistId = existing.getChecklistId();
        // every requested id is already on the checklist
        insertChecklistItemRow(checklistId, 1L, null);
        insertChecklistItemRow(checklistId, 2L, null);
        TestSubscriber<ItemChecklistState> updatedSubscriber =
                mItemChecklistChangeNotifier.getChecklistUpdatedFlow().test();

        int added = mItemChecklistService.addItemIds(checklistId,
                Arrays.asList(1L, 2L));

        assertEquals(0, added);
        // no row was inserted and the timestamp was NOT bumped
        assertEquals(2,
                mItemChecklistDao.findItemChecklistItemsByChecklistId(checklistId).size());
        assertEquals(OLD_DATE,
                mItemChecklistDao.findAllItemChecklists().get(0).updatedDateTime);
        updatedSubscriber.assertNoValues();
        updatedSubscriber.assertNoErrors();
    }

    @Test
    public void uncheckItemIdsBumpsAndNotifiesWhenRowsCleared() {
        ItemChecklistState existing = insertChecklist("Shopping list");
        long checklistId = existing.getChecklistId();
        insertChecklistItemRow(checklistId, 1L, new Date(1000L));
        insertChecklistItemRow(checklistId, 2L, new Date(1000L));
        TestSubscriber<ItemChecklistState> updatedSubscriber =
                mItemChecklistChangeNotifier.getChecklistUpdatedFlow().test();

        int unchecked = mItemChecklistService.uncheckItemIds(checklistId,
                Arrays.asList(1L, 2L));

        assertEquals(2, unchecked);
        // both rows moved back to the open section
        List<ItemChecklistItem> rows =
                mItemChecklistDao.findItemChecklistItemsByChecklistId(checklistId);
        assertEquals(2, rows.size());
        for (ItemChecklistItem row : rows) {
            assertNull(row.checkedDateTime);
        }
        // the checklist row changed even though no item row was inserted:
        // its timestamp is bumped and open detail pages are notified
        assertTrue(mItemChecklistDao.findAllItemChecklists().get(0)
                .updatedDateTime.after(OLD_DATE));
        updatedSubscriber.awaitCount(1);
        updatedSubscriber.assertValueCount(1);
        // the notifier state must be a fresh re-read clone, not the caller's
        // mutated state instance
        ItemChecklistState notified = updatedSubscriber.values().get(0);
        assertNotSame(existing, notified);
        assertEquals(checklistId, notified.getChecklistId().longValue());
        assertTrue(notified.getUpdatedDateTime().after(OLD_DATE));
    }

    @Test
    public void uncheckItemIdsWithNothingClearedDoesNotTouchDaoOrNotifier() {
        ItemChecklistState existing = insertChecklist("Shopping list");
        long checklistId = existing.getChecklistId();
        // both rows are already unchecked: nothing to clear
        insertChecklistItemRow(checklistId, 1L, null);
        insertChecklistItemRow(checklistId, 2L, null);
        TestSubscriber<ItemChecklistState> updatedSubscriber =
                mItemChecklistChangeNotifier.getChecklistUpdatedFlow().test();

        int unchecked = mItemChecklistService.uncheckItemIds(checklistId,
                Collections.singletonList(1L));

        assertEquals(0, unchecked);
        // no row changed: no timestamp bump, no notifier event
        assertEquals(OLD_DATE,
                mItemChecklistDao.findAllItemChecklists().get(0).updatedDateTime);
        updatedSubscriber.assertNoValues();
        updatedSubscriber.assertNoErrors();
    }

    @Test
    public void updateChecklistBumpsTimestampSavesAndNotifiesWithClone() {
        ItemChecklistState state = insertChecklist("Shopping list");
        TestSubscriber<ItemChecklistState> updatedSubscriber =
                mItemChecklistChangeNotifier.getChecklistUpdatedFlow().test();

        ItemChecklistState returned = mItemChecklistService.updateChecklist(state);

        assertSame(state, returned);
        assertTrue(state.getUpdatedDateTime().after(OLD_DATE));
        // the bump is really persisted
        ItemChecklist checklist = mItemChecklistDao.findAllItemChecklists().get(0);
        assertTrue(checklist.updatedDateTime.after(OLD_DATE));
        assertEquals(state.getUpdatedDateTime(), checklist.updatedDateTime);
        updatedSubscriber.awaitCount(1);
        updatedSubscriber.assertValueCount(1);
        // the notifier receives a clone taken AFTER the bump: subscribers see
        // the new timestamp but cannot mutate the caller's state
        ItemChecklistState notified = updatedSubscriber.values().get(0);
        assertNotSame(state, notified);
        assertNotSame(state.getItemChecklist(), notified.getItemChecklist());
        assertTrue(notified.getUpdatedDateTime().after(OLD_DATE));
        assertEquals("Shopping list", notified.getTitle());
    }

    @Test
    public void deleteChecklistDeletesAndNotifiesWithClone() {
        ItemChecklistState state = insertChecklist("Shopping list");
        insertChecklistItemRow(state.getChecklistId(), 1L, null);
        TestSubscriber<ItemChecklistState> deletedSubscriber =
                mItemChecklistChangeNotifier.getChecklistDeletedFlow().test();

        ItemChecklistState returned = mItemChecklistService.deleteChecklist(state);

        assertSame(state, returned);
        // the checklist row AND its item rows are gone (one DAO transaction)
        assertTrue(mItemChecklistDao.findAllItemChecklists().isEmpty());
        assertTrue(mItemChecklistDao.findAllItemChecklistItems().isEmpty());
        deletedSubscriber.awaitCount(1);
        deletedSubscriber.assertValueCount(1);
        ItemChecklistState notified = deletedSubscriber.values().get(0);
        assertNotSame(state, notified);
        assertNotSame(state.getItemChecklist(), notified.getItemChecklist());
        assertEquals("Shopping list", notified.getTitle());
    }

    @Test
    public void updateChecklistItemBumpsParentUpdatesBothAndNotifiesWithFreshClone() {
        ItemChecklistState existing = insertChecklist("Shopping list");
        long checklistId = existing.getChecklistId();
        ItemChecklistItem item = insertChecklistItemRow(checklistId, 1L, null);
        // a check-off: the item row edit the caller persists
        item.checkedDateTime = new Date();
        TestSubscriber<ItemChecklistItem> itemUpdatedSubscriber =
                mItemChecklistChangeNotifier.getChecklistItemUpdatedFlow().test();
        TestSubscriber<ItemChecklistState> checklistUpdatedSubscriber =
                mItemChecklistChangeNotifier.getChecklistUpdatedFlow().test();

        ItemChecklistItem returned = mItemChecklistService.updateChecklistItem(item);

        assertSame(item, returned);
        // the item row edit is persisted ...
        ItemChecklistItem stored = mItemChecklistDao.findItemChecklistItemById(item.id);
        assertEquals(item.checkedDateTime, stored.checkedDateTime);
        // ... and the parent row is bumped inside the same @Transaction update
        ItemChecklist checklist = mItemChecklistDao.findAllItemChecklists().get(0);
        assertTrue(checklist.updatedDateTime.after(OLD_DATE));
        // the item-level notifier emits ONLY the ItemChecklistItem (the
        // notifier drops the state parameter — see
        // ItemChecklistChangeNotifier#checklistItemUpdated), so the former
        // unit test's item-level "not-same-instance" pin is intentionally
        // dropped here; the fresh-parent contract is pinned via the DAO
        // re-read above
        itemUpdatedSubscriber.awaitCount(1);
        itemUpdatedSubscriber.assertValueCount(1);
        assertEquals(item.id, itemUpdatedSubscriber.values().get(0).id);
        // the checklist-level updated flow is NOT fired by an item mutation
        checklistUpdatedSubscriber.assertNoValues();
        checklistUpdatedSubscriber.assertNoErrors();
    }

    @Test
    public void deleteChecklistItemBumpsParentDeletesThenReReadsAndNotifies() {
        ItemChecklistState existing = insertChecklist("Shopping list");
        long checklistId = existing.getChecklistId();
        ItemChecklistItem item = insertChecklistItemRow(checklistId, 1L, null);
        TestSubscriber<ItemChecklistItem> itemDeletedSubscriber =
                mItemChecklistChangeNotifier.getChecklistItemDeletedFlow().test();

        ItemChecklistItem returned = mItemChecklistService.deleteChecklistItem(item);

        assertSame(item, returned);
        // the item row is really gone ...
        assertNull(mItemChecklistDao.findItemChecklistItemById(item.id));
        // ... and the parent is bumped (before the deletion — the bump is
        // observable against the insertion-time OLD_DATE)
        assertTrue(mItemChecklistDao.findAllItemChecklists().get(0)
                .updatedDateTime.after(OLD_DATE));
        // the item-level notifier emits ONLY the removed ItemChecklistItem
        // (the notifier drops the state parameter), so the fresh-clone
        // parent pin of the former unit test is covered by the DAO re-reads
        // above instead
        itemDeletedSubscriber.awaitCount(1);
        itemDeletedSubscriber.assertValueCount(1);
        assertEquals(item.id, itemDeletedSubscriber.values().get(0).id);
    }

    @Test
    public void updateChecklistItemWithMissingParentFallsBackToBareUpdateWithoutNotifier() {
        // insert a real checklist + item row, then delete ONLY the parent
        // checklist row (item_checklist_item has no FK constraint on
        // item_checklist), leaving the orphan item row behind
        ItemChecklistState existing = insertChecklist("Shopping list");
        long checklistId = existing.getChecklistId();
        ItemChecklistItem item = insertChecklistItemRow(checklistId, 1L, null);
        mDb.getOpenHelper().getWritableDatabase()
                .execSQL("DELETE FROM item_checklist WHERE id = ?",
                        new Object[]{checklistId});
        // only the parent row is gone; the item row survives as an orphan
        assertTrue(mItemChecklistDao.findAllItemChecklists().isEmpty());
        assertEquals(1, mItemChecklistDao.findAllItemChecklistItems().size());
        // the check-off must not be lost even though the checklist
        // disappeared mid-flight
        item.checkedDateTime = new Date();
        TestSubscriber<ItemChecklistItem> itemUpdatedSubscriber =
                mItemChecklistChangeNotifier.getChecklistItemUpdatedFlow().test();

        ItemChecklistItem returned = mItemChecklistService.updateChecklistItem(item);

        assertSame(item, returned);
        // the bare item-row update still ran
        ItemChecklistItem stored = mItemChecklistDao.findItemChecklistItemById(item.id);
        assertEquals(item.checkedDateTime, stored.checkedDateTime);
        // no parent row: no bump possible and no notifier event
        itemUpdatedSubscriber.assertNoValues();
        itemUpdatedSubscriber.assertNoErrors();
    }

    @Test
    public void deleteChecklistItemWithMissingParentDeletesItemWithoutNotifier() {
        // same missing-parent scenario as the update fallback guard: the
        // deletion still proceeds, silently, without a notification
        ItemChecklistState existing = insertChecklist("Shopping list");
        long checklistId = existing.getChecklistId();
        ItemChecklistItem item = insertChecklistItemRow(checklistId, 1L, null);
        mDb.getOpenHelper().getWritableDatabase()
                .execSQL("DELETE FROM item_checklist WHERE id = ?",
                        new Object[]{checklistId});
        TestSubscriber<ItemChecklistItem> itemDeletedSubscriber =
                mItemChecklistChangeNotifier.getChecklistItemDeletedFlow().test();

        ItemChecklistItem returned = mItemChecklistService.deleteChecklistItem(item);

        assertSame(item, returned);
        // the deletion is not lost
        assertNull(mItemChecklistDao.findItemChecklistItemById(item.id));
        // no parent row: no notifier event
        itemDeletedSubscriber.assertNoValues();
        itemDeletedSubscriber.assertNoErrors();
    }

    private ItemChecklistState insertChecklist(String title) {
        ItemChecklist checklist = new ItemChecklist();
        checklist.title = title;
        checklist.createdDateTime = OLD_DATE;
        checklist.updatedDateTime = OLD_DATE;
        ItemChecklistState state = new ItemChecklistState();
        state.updateItemChecklist(checklist);
        mItemChecklistDao.insertItemChecklist(state);
        return state;
    }

    private ItemChecklistItem insertChecklistItemRow(long checklistId, long itemId,
                                                     Date checkedDateTime) {
        ItemChecklistItem item = new ItemChecklistItem();
        item.itemChecklistId = checklistId;
        item.itemId = itemId;
        item.checkedDateTime = checkedDateTime;
        item.createdDateTime = OLD_DATE;
        // insertItemChecklistItems does NOT write the generated rowid back
        // onto the entity (the List<Long> return carries it instead), so the
        // id must be assigned here for the findItemChecklistItemById asserts
        List<Long> insertedIds = mItemChecklistDao
                .insertItemChecklistItems(Collections.singletonList(item));
        item.id = insertedIds.get(0);
        return item;
    }

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemChecklistDao.class, () -> mItemChecklistDao);
            providerRegistry.register(ItemChecklistChangeNotifier.class,
                    () -> mItemChecklistChangeNotifier);
        }
    }
}
