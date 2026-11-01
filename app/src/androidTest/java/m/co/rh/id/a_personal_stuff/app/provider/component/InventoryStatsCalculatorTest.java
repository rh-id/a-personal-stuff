package m.co.rh.id.a_personal_stuff.app.provider.component;

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

import java.math.BigDecimal;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.base.test.DirectExecutorService;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchaseDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_purchase.room.ItemPurchaseDatabase;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_usage.room.ItemUsageDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Component tests for {@link InventoryStatsCalculator} against REAL
 * in-memory AppDatabase + ItemUsageDatabase + ItemPurchaseDatabase: the
 * signed stock deltas flow through the real usage/purchase dashboard
 * contributors over real DAOs, and every aggregate is computed by actual
 * Room SQL (count, SUM(price*amount), expiry and min-amount candidate
 * queries) instead of mocked DAO stubs.
 */
@RunWith(AndroidJUnit4.class)
public class InventoryStatsCalculatorTest {

    private static final long NOW_MILLIS = 1700000000000L;
    private static final long MILLIS_PER_DAY = 86400000L;

    private Context mContext;
    private AppDatabase mAppDb;
    private ItemUsageDatabase mUsageDb;
    private ItemPurchaseDatabase mPurchaseDb;
    private ItemDao mItemDao;
    private ItemUsageDao mItemUsageDao;
    private ItemPurchaseDao mItemPurchaseDao;
    private Provider mProvider;
    private InventoryStatsCalculator mCalculator;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mAppDb = Room.inMemoryDatabaseBuilder(mContext, AppDatabase.class)
                .allowMainThreadQueries().build();
        mUsageDb = Room.inMemoryDatabaseBuilder(mContext, ItemUsageDatabase.class)
                .allowMainThreadQueries().build();
        mPurchaseDb = Room.inMemoryDatabaseBuilder(mContext, ItemPurchaseDatabase.class)
                .allowMainThreadQueries().build();
        mItemDao = mAppDb.itemDao();
        mItemUsageDao = mUsageDb.itemUsageDao();
        mItemPurchaseDao = mPurchaseDb.itemPurchaseDao();
        mProvider = Provider.createProvider(mContext, new TestProviderModule());
        // the calculator consumes the signed per-item stock deltas through
        // the contributor contract: the REAL usage/purchase dashboard
        // contributors are registered by the module over the REAL DAOs
        // (usage −Σ, purchase +Σ)
        mCalculator = new InventoryStatsCalculator(mProvider);
    }

    @After
    public void tearDown() {
        if (mAppDb != null) {
            mAppDb.close();
        }
        if (mUsageDb != null) {
            mUsageDb.close();
        }
        if (mPurchaseDb != null) {
            mPurchaseDb.close();
        }
    }

    @Test
    public void computeWithEmptyDaosYieldsZeroedSummary() {
        InventoryStatsCalculator.AlertSummary alertSummary =
                mCalculator.compute(new Date(NOW_MILLIS), 7);

        assertEquals(0, alertSummary.totalItems);
        assertNull("inventory value must be null when there are no valued items",
                alertSummary.inventoryValue);
        assertEquals(0, alertSummary.expiredCount);
        assertEquals(0, alertSummary.expiringCount);
        assertTrue(alertSummary.expiringItems.isEmpty());
        assertTrue(alertSummary.lowStockItems.isEmpty());
    }

    @Test
    public void inventoryValueMatchesPriceTimesAmountAggregate() {
        // Σ price×amount is computed by the DAO in SQL (sumInventoryValue);
        // for a 3×2.50 + 4×0.99 inventory the aggregate is 3*2.50 + 4*0.99
        // and the calculator must surface it unchanged
        insertItem("Flour", 3, null, new BigDecimal("2.50"), null);
        insertItem("Salt", 4, null, new BigDecimal("0.99"), null);

        InventoryStatsCalculator.AlertSummary alertSummary =
                mCalculator.compute(new Date(NOW_MILLIS), 7);

        double expectedValue = 3 * 2.50 + 4 * 0.99;
        assertEquals(2, alertSummary.totalItems);
        assertEquals(expectedValue, alertSummary.inventoryValue, 0.0001);
    }

    @Test
    public void expiryBoundarySplitsExpiredAndExpiringWithinLeadDays() {
        // the DAO returns all in-stock items expiring at or before now+leadDays
        // (open lower bound): items at or before now are expired, the rest
        // are expiring soon
        insertItem("Milk", 1, null, null,
                new Date(NOW_MILLIS - MILLIS_PER_DAY));                    // past → expired
        insertItem("Yogurt", 1, null, null, new Date(NOW_MILLIS));         // exactly now → expired
        insertItem("Cheese", 1, null, null,
                new Date(NOW_MILLIS + 3 * MILLIS_PER_DAY));                // within lead → expiring
        insertItem("Butter", 1, null, null,
                new Date(NOW_MILLIS + 7 * MILLIS_PER_DAY));                // at lead end → expiring

        InventoryStatsCalculator.AlertSummary alertSummary =
                mCalculator.compute(new Date(NOW_MILLIS), 7);

        assertEquals(4, alertSummary.expiringItems.size());
        assertEquals(2, alertSummary.expiredCount);
        assertEquals(2, alertSummary.expiringCount);
    }

    @Test
    public void lowStockUsesDerivedRemainingStock() {
        // remaining = amount − Σusage.amount + Σpurchase.amount, low when <= minAmount
        Item notLowStock = insertItem("Flour", 10, 5, null, null); // remaining 10 − 4 + 1 = 7 > 5 → not low
        Item lowStock = insertItem("Salt", 5, 5, null, null);      // remaining 5 − 2 + 0 = 3 <= 5 → low
        insertUsage(notLowStock.id, 4);
        insertUsage(lowStock.id, 2);
        insertPurchase(notLowStock.id, 1);

        InventoryStatsCalculator.AlertSummary alertSummary =
                mCalculator.compute(new Date(NOW_MILLIS), 7);

        assertEquals(1, alertSummary.lowStockItems.size());
        assertEquals(Long.valueOf(lowStock.id), alertSummary.lowStockItems.get(0).id);
    }

    @Test
    public void findAlertItemIdsDeduplicatesExpiredAndLowStock() {
        // the item is both expired and low on stock: it must appear exactly
        // once when both alert types are requested
        Item both = insertItem("Milk", 5, 5, null,
                new Date(NOW_MILLIS - MILLIS_PER_DAY)); // remaining 5 − 2 = 3 <= 5 → low stock
        insertUsage(both.id, 2);

        List<Long> alertItemIds = mCalculator.findAlertItemIds(
                EnumSet.of(InventoryStatsCalculator.AlertType.EXPIRED,
                        InventoryStatsCalculator.AlertType.LOW_STOCK),
                new Date(NOW_MILLIS), 7);

        assertEquals(1, alertItemIds.size());
        assertEquals(Long.valueOf(both.id), alertItemIds.get(0));
    }

    @Test
    public void findAlertItemIdsCountsExpiryAtNowAsExpired() {
        // the expired/expiring split uses !expiredDateTime.after(now), so an
        // item whose expiry is exactly now is EXPIRED, not EXPIRING_SOON
        Item atNow = insertItem("Yogurt", 1, null, null, new Date(NOW_MILLIS));

        List<Long> expired = mCalculator.findAlertItemIds(
                EnumSet.of(InventoryStatsCalculator.AlertType.EXPIRED), new Date(NOW_MILLIS), 7);
        List<Long> expiringSoon = mCalculator.findAlertItemIds(
                EnumSet.of(InventoryStatsCalculator.AlertType.EXPIRING_SOON), new Date(NOW_MILLIS), 7);

        assertEquals(1, expired.size());
        assertEquals(Long.valueOf(atNow.id), expired.get(0));
        assertTrue("an item whose expiry is exactly now must not be expiring soon",
                expiringSoon.isEmpty());
    }

    @Test
    public void findAlertItemIdsReturnsOnlyRequestedTypes() {
        // requesting only LOW_STOCK must not leak expiring-only items
        Item expiringOnly = insertItem("Cheese", 1, null, null,
                new Date(NOW_MILLIS + 3 * MILLIS_PER_DAY));
        Item lowStockOnly = insertItem("Salt", 1, 3, null, null); // remaining 1 <= 3 → low stock

        List<Long> alertItemIds = mCalculator.findAlertItemIds(
                EnumSet.of(InventoryStatsCalculator.AlertType.LOW_STOCK), new Date(NOW_MILLIS), 7);

        assertEquals(1, alertItemIds.size());
        assertEquals(Long.valueOf(lowStockOnly.id), alertItemIds.get(0));
        assertTrue(!alertItemIds.contains(expiringOnly.id));
    }

    @Test
    public void findAlertItemIdsExcludesOutOfStockItemsFromExpiry() {
        // a fully used-up item (remaining stock <= 0) is not an expiry alert
        // regardless of its expiry date, matching the dashboard counts
        Item usedUp = insertItem("Milk", 5, null, null,
                new Date(NOW_MILLIS - MILLIS_PER_DAY));
        insertUsage(usedUp.id, 5);

        Set<InventoryStatsCalculator.AlertType> types =
                EnumSet.of(InventoryStatsCalculator.AlertType.EXPIRED,
                        InventoryStatsCalculator.AlertType.EXPIRING_SOON);
        List<Long> alertItemIds = mCalculator.findAlertItemIds(types, new Date(NOW_MILLIS), 7);

        assertTrue(alertItemIds.isEmpty());
    }

    private Item insertItem(String name, int amount, Integer minAmount,
                            BigDecimal price, Date expiredDateTime) {
        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = name;
        item.amount = amount;
        item.minAmount = minAmount;
        item.price = price;
        item.expiredDateTime = expiredDateTime;
        item.createdDateTime = new Date(NOW_MILLIS);
        item.updatedDateTime = new Date(NOW_MILLIS);
        itemState.updateItem(item);
        mItemDao.insertItem(itemState);
        return item;
    }

    private void insertUsage(long itemId, int amount) {
        ItemUsage itemUsage = new ItemUsage();
        itemUsage.itemId = itemId;
        itemUsage.description = "usage";
        itemUsage.amount = amount;
        itemUsage.createdDateTime = new Date(NOW_MILLIS);
        mItemUsageDao.insert(itemUsage);
    }

    private void insertPurchase(long itemId, int amount) {
        ItemPurchase itemPurchase = new ItemPurchase();
        itemPurchase.itemId = itemId;
        itemPurchase.description = "purchase";
        itemPurchase.amount = amount;
        itemPurchase.cost = BigDecimal.ZERO;
        itemPurchase.purchaseDateTime = new Date(NOW_MILLIS);
        itemPurchase.createdDateTime = new Date(NOW_MILLIS);
        mItemPurchaseDao.insert(itemPurchase);
    }

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, DirectExecutorService::new);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemDao.class, () -> mItemDao);
            providerRegistry.register(ItemUsageDao.class, () -> mItemUsageDao);
            providerRegistry.register(ItemPurchaseDao.class, () -> mItemPurchaseDao);
            providerRegistry.register(ItemChangeNotifier.class, ItemChangeNotifier::new);
            providerRegistry.register(ItemUsageChangeNotifier.class, ItemUsageChangeNotifier::new);
            providerRegistry.register(ItemPurchaseChangeNotifier.class, ItemPurchaseChangeNotifier::new);
            // eager registry (mirrors the app module) + the REAL dashboard
            // contributors over this test's DAOs
            providerRegistry.register(BackupContributorRegistry.class, BackupContributorRegistry::new);
            BackupContributorRegistry contributorRegistry = provider.get(BackupContributorRegistry.class);
            contributorRegistry.register(new ItemUsageDashboardContributor(provider));
            contributorRegistry.register(new ItemPurchaseDashboardContributor(provider));
        }
    }
}
