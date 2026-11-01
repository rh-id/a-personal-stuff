package m.co.rh.id.a_personal_stuff.app.provider.component;

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

import co.rh.id.lib.concurrent_utils.concurrent.executor.WeightedThreadPool;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.room.ItemChecklistDatabase;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.room.ItemMaintenanceDatabase;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.room.ItemPurchaseDatabase;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.room.ItemReminderDatabase;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.room.ItemUsageDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class OrphanSweepTest {
    private Context mContext;
    private AppDatabase mAppDb;
    private ItemUsageDatabase mUsageDb;
    private ItemPurchaseDatabase mPurchaseDb;
    private ItemMaintenanceDatabase mMaintenanceDb;
    private ItemReminderDatabase mReminderDb;
    private ItemChecklistDatabase mChecklistDb;
    private Provider mProvider;
    private ExecutorService mExecutorService;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mAppDb = Room.inMemoryDatabaseBuilder(mContext, AppDatabase.class)
                .allowMainThreadQueries().build();

        mUsageDb = Room.inMemoryDatabaseBuilder(mContext, ItemUsageDatabase.class)
                .allowMainThreadQueries().build();

        mPurchaseDb = Room.inMemoryDatabaseBuilder(mContext, ItemPurchaseDatabase.class)
                .allowMainThreadQueries().build();

        mMaintenanceDb = Room.inMemoryDatabaseBuilder(mContext, ItemMaintenanceDatabase.class)
                .allowMainThreadQueries().build();

        mReminderDb = Room.inMemoryDatabaseBuilder(mContext, ItemReminderDatabase.class)
                .allowMainThreadQueries().build();

        mChecklistDb = Room.inMemoryDatabaseBuilder(mContext, ItemChecklistDatabase.class)
                .allowMainThreadQueries().build();

        mProvider = Provider.createProvider(mContext, new TestOrphanSweepProviderModule());
        // capture the provider's executor (a fresh WeightedThreadPool per test)
        // so tearDown can shut it down
        mExecutorService = mProvider.get(ExecutorService.class);
    }

    @After
    public void tearDown() {
        // best-effort shared-state reset: stop this test's own executor pool
        // and close the in-memory DBs, each guarded so teardown can never
        // fail a test
        if (mExecutorService != null) {
            try {
                mExecutorService.shutdownNow();
            } catch (Exception ignored) {
            }
        }
        if (mAppDb != null) {
            mAppDb.close();
        }
        if (mUsageDb != null) {
            mUsageDb.close();
        }
        if (mPurchaseDb != null) {
            mPurchaseDb.close();
        }
        if (mMaintenanceDb != null) {
            mMaintenanceDb.close();
        }
        if (mReminderDb != null) {
            mReminderDb.close();
        }
        if (mChecklistDb != null) {
            mChecklistDb.close();
        }
    }

    @Test
    public void sweepRemovesChildRowsOfDeletedItem() {
        ItemState itemState = insertItem("Swept Item");
        long itemId = itemState.getItemId();
        Date now = new Date();

        // a surviving item keeps the valid id set non-empty (the sweep must
        // and does refuse to run against an empty item table, which it cannot
        // distinguish from a failed/partial read)
        insertItem("Anchor Item");

        ItemUsage usage = new ItemUsage();
        usage.itemId = itemId;
        usage.description = "orphan usage";
        usage.amount = 1;
        usage.createdDateTime = now;
        mUsageDb.itemUsageDao().insert(usage);

        ItemPurchase purchase = new ItemPurchase();
        purchase.itemId = itemId;
        purchase.description = "orphan purchase";
        purchase.amount = 1;
        purchase.purchaseDateTime = now;
        purchase.createdDateTime = now;
        mPurchaseDb.itemPurchaseDao().insert(purchase);

        ItemMaintenance maintenance = new ItemMaintenance();
        maintenance.itemId = itemId;
        maintenance.description = "orphan maintenance";
        maintenance.maintenanceDateTime = now;
        maintenance.createdDateTime = now;
        mMaintenanceDb.itemMaintenanceDao().insert(maintenance);

        ItemReminder reminder = new ItemReminder();
        reminder.itemId = itemId;
        reminder.reminderDateTime = new Date(now.getTime() + 86400000);
        reminder.message = "orphan reminder";
        mReminderDb.itemReminderDao().insertItemReminder(reminder);

        // simulate the crash window: delete the item directly via the DAO,
        // bypassing the ItemChangeNotifier so no event handler runs
        mAppDb.itemDao().deleteItem(itemState);

        new OrphanSweep(mProvider).sweep();

        assertTrue(mUsageDb.itemUsageDao().findAllItemUsages().isEmpty());
        assertTrue(mPurchaseDb.itemPurchaseDao().findAllItemPurchases().isEmpty());
        assertTrue(mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenances().isEmpty());
        assertTrue(mReminderDb.itemReminderDao().findAllItemReminders().isEmpty());
        // only the swept item is gone, the anchor survives untouched
        List<Item> remainingItems = mAppDb.itemDao().findAllItems();
        assertEquals(1, remainingItems.size());
        assertEquals("Anchor Item", remainingItems.get(0).name);
    }

    @Test
    public void sweepWithEmptyItemTableDeletesNothing() {
        // item table is empty: emptiness may mean failed/partial read, so the
        // sweep must never treat it as "delete all children"
        Date now = new Date();
        ItemUsage usage = new ItemUsage();
        usage.itemId = 999L;
        usage.description = "usage with no item at all";
        usage.amount = 1;
        usage.createdDateTime = now;
        mUsageDb.itemUsageDao().insert(usage);

        new OrphanSweep(mProvider).sweep();

        assertEquals(1, mUsageDb.itemUsageDao().findAllItemUsages().size());
    }

    @Test
    public void sweepKeepsChildRowsOfExistingItems() {
        ItemState itemState = insertItem("Alive Item");
        long itemId = itemState.getItemId();
        Date now = new Date();

        ItemUsage usage = new ItemUsage();
        usage.itemId = itemId;
        usage.description = "alive usage";
        usage.amount = 1;
        usage.createdDateTime = now;
        mUsageDb.itemUsageDao().insert(usage);

        ItemMaintenance maintenance = new ItemMaintenance();
        maintenance.itemId = itemId;
        maintenance.description = "alive maintenance";
        maintenance.maintenanceDateTime = now;
        maintenance.createdDateTime = now;
        mMaintenanceDb.itemMaintenanceDao().insert(maintenance);

        new OrphanSweep(mProvider).sweep();

        assertEquals(1, mAppDb.itemDao().findAllItems().size());
        assertEquals(1, mUsageDb.itemUsageDao().findAllItemUsages().size());
        assertEquals(1, mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenances().size());
    }

    @Test
    public void sweepRemovesOrphanedChecklistItemsButKeepsChecklistParent() {
        ItemState itemState = insertItem("Checklist Orphan Item");
        long itemId = itemState.getItemId();
        Date now = new Date();

        // a surviving item keeps the valid id set non-empty (the sweep must
        // and does refuse to run against an empty item table, which it cannot
        // distinguish from a failed/partial read)
        insertItem("Anchor Item");

        ItemChecklist checklist = new ItemChecklist();
        checklist.title = "Surviving checklist";
        checklist.createdDateTime = now;
        checklist.updatedDateTime = now;
        ItemChecklistState checklistState = new ItemChecklistState();
        checklistState.updateItemChecklist(checklist);
        mChecklistDb.itemChecklistDao().insertItemChecklist(checklistState);

        ItemChecklistItem checklistItem = new ItemChecklistItem();
        checklistItem.itemChecklistId = checklistState.getChecklistId();
        checklistItem.itemId = itemId;
        checklistItem.createdDateTime = now;
        mChecklistDb.itemChecklistDao().insertItemChecklistItems(Collections.singletonList(checklistItem));

        mAppDb.itemDao().deleteItem(itemState);

        new OrphanSweep(mProvider).sweep();

        // only item_checklist_item rows are swept: the parent item_checklist
        // row has no item_id and intentionally survives as an empty checklist
        assertTrue(mChecklistDb.itemChecklistDao().findAllItemChecklistItems().isEmpty());
        List<ItemChecklist> checklists = mChecklistDb.itemChecklistDao().findAllItemChecklists();
        assertEquals(1, checklists.size());
        assertEquals("Surviving checklist", checklists.get(0).title);
        // the anchor item survives untouched
        List<Item> remainingItems = mAppDb.itemDao().findAllItems();
        assertEquals(1, remainingItems.size());
        assertEquals("Anchor Item", remainingItems.get(0).name);
    }

    private ItemState insertItem(String name) {
        Date now = new Date();
        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = name;
        item.amount = 1;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);
        mAppDb.itemDao().insertItem(itemState);
        return itemState;
    }

    private class TestOrphanSweepProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, this::getExecutorService);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemDao.class, this::getItemDao);
            providerRegistry.register(ItemUsageDao.class, this::getItemUsageDao);
            providerRegistry.register(ItemPurchaseDao.class, this::getItemPurchaseDao);
            providerRegistry.register(ItemMaintenanceDao.class, this::getItemMaintenanceDao);
            providerRegistry.register(ItemReminderDao.class, this::getItemReminderDao);
            providerRegistry.register(ItemChecklistDao.class, this::getItemChecklistDao);
        }

        private ExecutorService getExecutorService() {
            // thread pool to be used throughout this app lifecycle
            WeightedThreadPool weightedThreadPool = new WeightedThreadPool();
            weightedThreadPool.setMaxWeight(5);
            return weightedThreadPool;
        }

        private ItemDao getItemDao() {
            return mAppDb.itemDao();
        }

        private ItemUsageDao getItemUsageDao() {
            return mUsageDb.itemUsageDao();
        }

        private ItemPurchaseDao getItemPurchaseDao() {
            return mPurchaseDb.itemPurchaseDao();
        }

        private ItemMaintenanceDao getItemMaintenanceDao() {
            return mMaintenanceDb.itemMaintenanceDao();
        }

        private ItemReminderDao getItemReminderDao() {
            return mReminderDb.itemReminderDao();
        }

        private ItemChecklistDao getItemChecklistDao() {
            return mChecklistDb.itemChecklistDao();
        }
    }
}
