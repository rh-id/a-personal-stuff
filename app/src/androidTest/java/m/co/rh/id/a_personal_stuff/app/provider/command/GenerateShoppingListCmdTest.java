package m.co.rh.id.a_personal_stuff.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.component.InventoryStatsCalculator;
import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.base.test.DirectExecutorService;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.component.ItemChecklistService;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_checklist.room.ItemChecklistDatabase;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchaseDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_purchase.room.ItemPurchaseDatabase;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_usage.room.ItemUsageDatabase;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Component tests for {@link GenerateShoppingListCmd} against REAL
 * components: in-memory AppDatabase/ItemUsageDatabase/ItemPurchaseDatabase/
 * ItemChecklistDatabase, the real {@link InventoryStatsCalculator} (fed by
 * the real usage/purchase dashboard contributors), the real
 * {@link ItemChecklistService} and the real {@link SettingsSharedPreferences}
 * (its prefs file is wiped synchronously before every test). No mocked
 * component: find-or-create, id persistence, adoption and regeneration all
 * run against actual Room SQL and committed SharedPreferences.
 */
@RunWith(AndroidJUnit4.class)
public class GenerateShoppingListCmdTest {

    /**
     * The ACTUAL prefs file name used by {@link SettingsSharedPreferences}
     * (its private SHARED_PREFERENCES_NAME constant): wiped in setUp so the
     * designated-list id starts at its 0 sentinel, and reset in tearDown.
     */
    private static final String SETTINGS_PREFS_NAME = "SettingsSharedPreferences";
    private static final Date OLD_DATE = new Date(0L);

    private Context mContext;
    private AppDatabase mAppDb;
    private ItemUsageDatabase mUsageDb;
    private ItemPurchaseDatabase mPurchaseDb;
    private ItemChecklistDatabase mChecklistDb;
    private ExecutorService mExecutorService;
    private Provider mProvider;
    private String mShoppingListTitle;
    /**
     * Non-null only in the concurrency test: switches the module's
     * InventoryStatsCalculator registration to the latch subclass that
     * forces both threads to overlap before the find-or-create lock.
     */
    private volatile CountDownLatch mOverlapLatch;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // synchronously clear the REAL settings prefs BEFORE the provider (and
        // with it SettingsSharedPreferences) is created, so every test starts
        // with the DEFAULT_SHOPPING_LIST_CHECKLIST_ID sentinel (0)
        assertTrue(mContext.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit());

        mAppDb = Room.inMemoryDatabaseBuilder(mContext, AppDatabase.class)
                .allowMainThreadQueries().build();
        mUsageDb = Room.inMemoryDatabaseBuilder(mContext, ItemUsageDatabase.class)
                .allowMainThreadQueries().build();
        mPurchaseDb = Room.inMemoryDatabaseBuilder(mContext, ItemPurchaseDatabase.class)
                .allowMainThreadQueries().build();
        mChecklistDb = Room.inMemoryDatabaseBuilder(mContext, ItemChecklistDatabase.class)
                .allowMainThreadQueries().build();
        mExecutorService = new DirectExecutorService();
        mProvider = Provider.createProvider(mContext, new TestShoppingListProviderModule());
        // localized title resolved from the real resource so the assertions
        // survive locale-driven capitalization differences
        mShoppingListTitle = mContext.getString(R.string.shopping_list_title);
    }

    @After
    public void tearDown() {
        // reset the designated shopping-list id to 0 (the whole prefs file is
        // test-owned) and close every in-memory DB
        mContext.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        if (mAppDb != null) {
            mAppDb.close();
        }
        if (mUsageDb != null) {
            mUsageDb.close();
        }
        if (mPurchaseDb != null) {
            mPurchaseDb.close();
        }
        if (mChecklistDb != null) {
            mChecklistDb.close();
        }
    }

    @Test
    public void createsChecklistAndPersistsIdWhenNothingStored() {
        insertLowStockItem("Beans", 1, 3); // remaining 1 <= 3 → low stock

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertTrue("a fresh checklist must be created", result.checklistId > 0);
        assertEquals(1, result.addedCount);
        assertEquals(1, result.lowStockCount);
        // exactly one checklist row with the localized designated title exists
        List<ItemChecklist> checklists =
                mChecklistDb.itemChecklistDao().findAllItemChecklists();
        assertEquals(1, checklists.size());
        assertEquals(mShoppingListTitle, checklists.get(0).title);
        assertEquals(Long.valueOf(result.checklistId),
                checklists.get(0).id);
        // the new designated id must be persisted, otherwise every tap would
        // fall through to the title lookup again
        assertEquals(result.checklistId,
                settings().getShoppingListChecklistId());
    }

    @Test
    public void usesStoredChecklistWhenIdStillExists() {
        Item beans = insertLowStockItem("Beans", 1, 3);
        Item salt = insertLowStockItem("Salt", 2, 5); // remaining 2 <= 5 → low stock
        ItemChecklist existing = insertChecklist(mShoppingListTitle);
        settings().setShoppingListChecklistId(existing.id);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(existing.id.longValue(), result.checklistId);
        assertEquals(2, result.addedCount);
        assertEquals(2, result.lowStockCount);
        // no second list: the stored checklist was reused, both low-stock
        // items appended as unchecked rows
        List<ItemChecklist> checklists =
                mChecklistDb.itemChecklistDao().findAllItemChecklists();
        assertEquals(1, checklists.size());
        List<ItemChecklistItem> rows = mChecklistDb.itemChecklistDao()
                .findItemChecklistItemsByChecklistId(existing.id);
        assertEquals(2, rows.size());
        for (ItemChecklistItem row : rows) {
            assertNull(row.checkedDateTime);
            assertTrue(row.itemId == beans.id || row.itemId == salt.id);
        }
    }

    @Test
    public void adoptsExistingListByTitleWhenStoredIdIsStale() {
        Item beans = insertLowStockItem("Beans", 1, 3);
        ItemChecklist adopted = insertChecklist(mShoppingListTitle);
        // a stale id that no longer exists (AUTOINCREMENT ids are never
        // reissued, so this dangling id stays dangling)
        settings().setShoppingListChecklistId(9999L);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        // the same-title list is adopted, not duplicated
        assertEquals(adopted.id.longValue(), result.checklistId);
        assertEquals(1, result.addedCount);
        // adoption is a one-time repair: the recovered id is re-persisted
        assertEquals(adopted.id.longValue(),
                settings().getShoppingListChecklistId());
        assertEquals(1, mChecklistDb.itemChecklistDao().findAllItemChecklists().size());
        List<ItemChecklistItem> rows = mChecklistDb.itemChecklistDao()
                .findItemChecklistItemsByChecklistId(adopted.id);
        assertEquals(1, rows.size());
        assertEquals(Long.valueOf(beans.id), rows.get(0).itemId);
    }

    @Test
    public void reTapWithNothingNewReportsZeroAdded() {
        Item beans = insertLowStockItem("Beans", 1, 3);
        ItemChecklist existing = insertChecklist(mShoppingListTitle);
        settings().setShoppingListChecklistId(existing.id);
        // the low-stock item is already on the list (unchecked row)
        insertChecklistItemRow(existing.id, beans.id, null);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(existing.id.longValue(), result.checklistId);
        assertEquals(0, result.addedCount);
        assertEquals(1, result.lowStockCount);
        // no duplicate row appeared and the row stayed unchecked
        List<ItemChecklistItem> rows = mChecklistDb.itemChecklistDao()
                .findItemChecklistItemsByChecklistId(existing.id);
        assertEquals(1, rows.size());
        assertNull(rows.get(0).checkedDateTime);
    }

    @Test
    public void emptyLowStockSkipsFindOrCreateAndAdd() {
        // an item WITHOUT a min amount is not a low-stock candidate
        insertItem("Flour", 10, null);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(0, result.lowStockCount);
        assertEquals(0, result.addedCount);
        assertEquals(0L, result.checklistId);
        // nothing to place: the designated checklist must not be touched,
        // let alone created
        assertTrue(mChecklistDb.itemChecklistDao().findAllItemChecklists().isEmpty());
        assertTrue(mChecklistDb.itemChecklistDao().findAllItemChecklistItems().isEmpty());
        assertEquals(0L, settings().getShoppingListChecklistId());
    }

    @Test
    public void concurrentExecutesCreateChecklistOnlyOnce() throws Exception {
        insertLowStockItem("Beans", 1, 3);
        // force both threads to overlap before the find-or-create lock via
        // the latch-aware calculator registered by the module
        mOverlapLatch = new CountDownLatch(2);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();
        Thread threadA = new Thread(() -> {
            try {
                cmd.execute().blockingGet();
            } catch (Throwable throwable) {
                errorA.set(throwable);
            }
        });
        Thread threadB = new Thread(() -> {
            try {
                cmd.execute().blockingGet();
            } catch (Throwable throwable) {
                errorB.set(throwable);
            }
        });
        threadA.start();
        threadB.start();
        threadA.join(10000);
        threadB.join(10000);

        assertNull(errorA.get());
        assertNull(errorB.get());
        // the real regression pin: without the find-or-create lock both
        // threads would pass the "no designated list" check and create two
        // same-title checklists — the real DB must hold exactly one
        List<ItemChecklist> checklists =
                mChecklistDb.itemChecklistDao().findAllItemChecklists();
        assertEquals(1, checklists.size());
        assertEquals(mShoppingListTitle, checklists.get(0).title);
    }

    @Test
    public void regenerationUnchecksCheckedRowsBeforeAdding() {
        Item beans = insertLowStockItem("Beans", 1, 3);
        Item salt = insertLowStockItem("Salt", 2, 5);
        ItemChecklist existing = insertChecklist(mShoppingListTitle);
        settings().setShoppingListChecklistId(existing.id);
        // a fully-checked stale list must re-open: regeneration un-checks the
        // low-stock rows before the (already-present) rows are evaluated
        insertChecklistItemRow(existing.id, beans.id, new Date(1000L));
        insertChecklistItemRow(existing.id, salt.id, new Date(1000L));

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(existing.id.longValue(), result.checklistId);
        assertEquals(2, result.lowStockCount);
        // both rows are back in the open section AND no unchecked duplicate
        // was appended (the real Service's addItemIds skips existing ids)
        List<ItemChecklistItem> rows = mChecklistDb.itemChecklistDao()
                .findItemChecklistItemsByChecklistId(existing.id);
        assertEquals(2, rows.size());
        for (ItemChecklistItem row : rows) {
            assertNull(row.checkedDateTime);
        }
    }

    @Test
    public void regenerationWithNothingToUncheckStillAddsItems() {
        Item beans = insertLowStockItem("Beans", 1, 3);
        ItemChecklist existing = insertChecklist(mShoppingListTitle);
        settings().setShoppingListChecklistId(existing.id);
        // empty list: nothing checked, regeneration is a no-op but the
        // append must still run

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(existing.id.longValue(), result.checklistId);
        assertEquals(1, result.addedCount);
        List<ItemChecklistItem> rows = mChecklistDb.itemChecklistDao()
                .findItemChecklistItemsByChecklistId(existing.id);
        assertEquals(1, rows.size());
        assertNull(rows.get(0).checkedDateTime);
        assertEquals(Long.valueOf(beans.id), rows.get(0).itemId);
    }

    private SettingsSharedPreferences settings() {
        return mProvider.get(SettingsSharedPreferences.class);
    }

    private Item insertLowStockItem(String name, int amount, int minAmount) {
        return insertItem(name, amount, Integer.valueOf(minAmount));
    }

    private Item insertItem(String name, int amount, Integer minAmount) {
        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = name;
        item.amount = amount;
        item.minAmount = minAmount;
        item.createdDateTime = OLD_DATE;
        item.updatedDateTime = OLD_DATE;
        itemState.updateItem(item);
        mAppDb.itemDao().insertItem(itemState);
        return item;
    }

    private ItemChecklist insertChecklist(String title) {
        ItemChecklist checklist = new ItemChecklist();
        checklist.title = title;
        checklist.createdDateTime = OLD_DATE;
        checklist.updatedDateTime = OLD_DATE;
        ItemChecklistState state = new ItemChecklistState();
        state.updateItemChecklist(checklist);
        mChecklistDb.itemChecklistDao().insertItemChecklist(state);
        return checklist;
    }

    private void insertChecklistItemRow(long checklistId, long itemId, Date checkedDateTime) {
        ItemChecklistItem item = new ItemChecklistItem();
        item.itemChecklistId = checklistId;
        item.itemId = itemId;
        item.checkedDateTime = checkedDateTime;
        item.createdDateTime = OLD_DATE;
        mChecklistDb.itemChecklistDao()
                .insertItemChecklistItems(Collections.singletonList(item));
    }

    /**
     * Overrides {@code findAlertItemIds} to hold both threads in the
     * pre-lock section so they race into find-or-create together; bounded
     * await so a non-overlapping run cannot hang the test.
     */
    private static class LatchInventoryStatsCalculator extends InventoryStatsCalculator {
        private final CountDownLatch mLatch;

        private LatchInventoryStatsCalculator(Provider provider, CountDownLatch latch) {
            super(provider);
            mLatch = latch;
        }

        @Override
        public List<Long> findAlertItemIds(Set<InventoryStatsCalculator.AlertType> types,
                                           Date now, int leadDays) {
            mLatch.countDown();
            try {
                mLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            return super.findAlertItemIds(types, now, leadDays);
        }
    }

    private class TestShoppingListProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            // the direct executor runs every command and prefs write inline
            // on the calling thread so the blockingGet chains are deterministic
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemDao.class, () -> mAppDb.itemDao());
            providerRegistry.register(ItemUsageDao.class, () -> mUsageDb.itemUsageDao());
            providerRegistry.register(ItemPurchaseDao.class, () -> mPurchaseDb.itemPurchaseDao());
            providerRegistry.register(ItemChangeNotifier.class, ItemChangeNotifier::new);
            providerRegistry.register(ItemUsageChangeNotifier.class, ItemUsageChangeNotifier::new);
            providerRegistry.register(ItemPurchaseChangeNotifier.class, ItemPurchaseChangeNotifier::new);
            // real contributor registry + the REAL dashboard contributors, so
            // InventoryStatsCalculator's derived stock deltas are genuine
            providerRegistry.register(BackupContributorRegistry.class, BackupContributorRegistry::new);
            BackupContributorRegistry contributorRegistry = provider.get(BackupContributorRegistry.class);
            contributorRegistry.register(new ItemUsageDashboardContributor(provider));
            contributorRegistry.register(new ItemPurchaseDashboardContributor(provider));
            providerRegistry.register(ItemChecklistDao.class, () -> mChecklistDb.itemChecklistDao());
            providerRegistry.register(ItemChecklistChangeNotifier.class, ItemChecklistChangeNotifier::new);
            providerRegistry.registerLazy(ItemChecklistService.class,
                    () -> new ItemChecklistService(provider));
            providerRegistry.registerLazy(SettingsSharedPreferences.class,
                    () -> new SettingsSharedPreferences(provider));
            // factory (not singleton) so the concurrency test can swap in the
            // latch-aware calculator via mOverlapLatch before the cmd resolves it
            providerRegistry.registerFactory(InventoryStatsCalculator.class, () -> {
                CountDownLatch latch = mOverlapLatch;
                return latch == null
                        ? new InventoryStatsCalculator(provider)
                        : new LatchInventoryStatsCalculator(provider, latch);
            });
        }
    }
}
