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

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_personal_stuff.app.provider.component.InventoryStatsCalculator;
import m.co.rh.id.a_personal_stuff.app.provider.service.StatisticsService;
import m.co.rh.id.a_personal_stuff.app.testutil.TestSeeds;
import m.co.rh.id.a_personal_stuff.app.ui.model.StatisticsData;
import m.co.rh.id.a_personal_stuff.base.contributor.ModuleContributorRegistry;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.base.test.DirectExecutorService;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchaseDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_purchase.room.ItemPurchaseDatabase;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_usage.room.ItemUsageDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Command tests for {@link StatisticsCmd} (and the {@link
 * m.co.rh.id.a_personal_stuff.app.provider.service.StatisticsService} engine
 * behind it) against REAL in-memory AppDatabase + ItemUsageDatabase +
 * ItemPurchaseDatabase. The shared weight-5 executor is replaced with the
 * {@link DirectExecutorService} test fixture so execute() runs inline and
 * blockingGet is deterministic.
 */
@RunWith(AndroidJUnit4.class)
public class StatisticsCmdTest {

    private Context mContext;
    private AppDatabase mAppDb;
    private ItemUsageDatabase mUsageDb;
    private ItemPurchaseDatabase mPurchaseDb;
    private ItemDao mItemDao;
    private ItemUsageDao mItemUsageDao;
    private ItemPurchaseDao mItemPurchaseDao;
    private Provider mProvider;
    private StatisticsCmd mStatisticsCmd;

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
        mStatisticsCmd = new StatisticsCmd(mProvider);
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
    public void monthlySpendIsGapFilledToTwelveAscendingMonths() {
        Date now = new Date();
        // purchases 5 and 2 months before the current month leave the rest
        // of the window empty: the series must still contain exactly 12
        // months, ascending, with the missing ones gap-filled at 0
        Date fiveMonthsAgo = monthsBefore(now, 5);
        Date twoMonthsAgo = monthsBefore(now, 2);
        mItemPurchaseDao.insert(TestSeeds.itemPurchase(0, new BigDecimal("100.00"),
                fiveMonthsAgo, now));
        mItemPurchaseDao.insert(TestSeeds.itemPurchase(0, new BigDecimal("50.00"),
                twoMonthsAgo, now));
        SimpleDateFormat monthKeyFormat = new SimpleDateFormat("yyyy-MM", Locale.US);
        String expectedCurrentMonth = monthKeyFormat.format(now);

        StatisticsData statisticsData = mStatisticsCmd.execute().blockingGet();

        List<StatisticsData.MonthlySpend> monthlySpendList = statisticsData.monthlySpend;
        assertEquals("the spend series must be a continuous 12-month window",
                12, monthlySpendList.size());
        // ascending (oldest first)
        for (int i = 1; i < monthlySpendList.size(); i++) {
            assertTrue("month keys must ascend: " + monthlySpendList,
                    monthlySpendList.get(i - 1).month
                            .compareTo(monthlySpendList.get(i).month) < 0);
        }
        assertEquals("the newest month of the window is the current month",
                expectedCurrentMonth, monthlySpendList.get(11).month);
        assertEquals(100.00, monthlySpendList.get(6).total, 0.0001);
        assertEquals(50.00, monthlySpendList.get(9).total, 0.0001);
        // gap-filled month between the two purchase months
        assertEquals(0.0, monthlySpendList.get(7).total, 0.0001);
        assertEquals(0.0, monthlySpendList.get(0).total, 0.0001);
    }

    @Test
    public void mostConsumedIsRankedDescendingWithResolvedNames() {
        Date now = new Date();
        // Beta total 9 (5+4), Alpha total 7, Gamma total 2 → ranked Beta,
        // Alpha, Gamma with the item names resolved via ItemDao
        insertItemWithUsages("Alpha", now, 3, 4);
        insertItemWithUsages("Beta", now, 5, 4);
        insertItemWithUsages("Gamma", now, 2);

        StatisticsData statisticsData = mStatisticsCmd.execute().blockingGet();

        List<StatisticsData.MostConsumedItem> mostConsumedItems =
                statisticsData.mostConsumedItems;
        assertEquals(3, mostConsumedItems.size());
        assertEquals("Beta", mostConsumedItems.get(0).itemName);
        assertEquals(9, mostConsumedItems.get(0).totalAmount);
        assertEquals("Alpha", mostConsumedItems.get(1).itemName);
        assertEquals(7, mostConsumedItems.get(1).totalAmount);
        assertEquals("Gamma", mostConsumedItems.get(2).itemName);
        assertEquals(2, mostConsumedItems.get(2).totalAmount);
    }

    @Test
    public void mostConsumedIsCappedAtFive() {
        Date now = new Date();
        // six items with usage must yield only the top 5 rows
        for (int i = 1; i <= 6; i++) {
            insertItemWithUsages("Item " + i, now, i);
        }

        StatisticsData statisticsData = mStatisticsCmd.execute().blockingGet();

        assertEquals(5, statisticsData.mostConsumedItems.size());
        assertEquals("Item 6", statisticsData.mostConsumedItems.get(0).itemName);
        assertEquals("Item 2", statisticsData.mostConsumedItems.get(4).itemName);
    }

    @Test
    public void inventoryValueAndExpiredCountAreCorrect() {
        Date now = new Date();
        // inventory value = Σ price × amount over ALL items;
        // expired = in-stock items with expired_date_time at or before now —
        // the dashboard's rule (InventoryStatsCalculator.countExpiredInStock):
        // a fully used-up expired item (remaining stock 0) is NOT counted
        insertItem("Flour", 3, new BigDecimal("2.50"), null, now);
        insertItem("Salt", 4, new BigDecimal("0.99"), null, now);
        insertItem("Milk", 1, null, new Date(now.getTime() - 86400000L), now);
        insertItem("Cheese", 1, null, new Date(now.getTime() + 86400000L), now);
        // Yogurt: amount 1, fully used up (usage 1) and expired → excluded
        Item usedUpExpired = TestSeeds.item("Yogurt", now);
        usedUpExpired.amount = 1;
        usedUpExpired.expiredDateTime = new Date(now.getTime() - 86400000L);
        long usedUpItemId = TestSeeds.insertItemWithRelations(mAppDb, usedUpExpired, null, null);
        ItemUsage usedUpUsage = TestSeeds.itemUsage(usedUpItemId, "used up", now);
        usedUpUsage.amount = 1;
        mItemUsageDao.insert(usedUpUsage);

        StatisticsData statisticsData = mStatisticsCmd.execute().blockingGet();

        assertEquals(3 * 2.50 + 4 * 0.99, statisticsData.inventoryValue, 0.0001);
        assertEquals(1, statisticsData.expiredCount);
    }

    @Test
    public void emptyDatabasesYieldNullInventoryValueAndEmptyRanking() {
        StatisticsData statisticsData = mStatisticsCmd.execute().blockingGet();

        assertNull("inventory value must be null when there are no valued items",
                statisticsData.inventoryValue);
        assertTrue(statisticsData.mostConsumedItems.isEmpty());
        assertEquals(0, statisticsData.expiredCount);
        assertEquals(12, statisticsData.monthlySpend.size());
        assertTrue("no spend anywhere → the page shows the empty state",
                statisticsData.isEmpty());
    }

    private Date monthsBefore(Date date, int months) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MONTH, -months);
        return calendar.getTime();
    }

    private void insertItem(String name, int amount, BigDecimal price,
                            Date expiredDateTime, Date now) {
        Item item = TestSeeds.item(name, now);
        item.amount = amount;
        item.price = price;
        item.expiredDateTime = expiredDateTime;
        TestSeeds.insertItemWithRelations(mAppDb, item, null, null);
    }

    private void insertItemWithUsages(String name, Date now, int... usageAmounts) {
        Item item = TestSeeds.item(name, now);
        long itemId = TestSeeds.insertItemWithRelations(mAppDb, item, null, null);
        for (int usageAmount : usageAmounts) {
            ItemUsage itemUsage = TestSeeds.itemUsage(itemId, "usage", now);
            itemUsage.amount = usageAmount;
            mItemUsageDao.insert(itemUsage);
        }
    }

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, DirectExecutorService::new);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemDao.class, () -> mItemDao);
            providerRegistry.register(ItemUsageDao.class, () -> mItemUsageDao);
            providerRegistry.register(ItemPurchaseDao.class, () -> mItemPurchaseDao);
            // the expired count now flows through InventoryStatsCalculator,
            // which consumes the signed stock deltas via the contributor
            // contract: register the REAL usage/purchase dashboard
            // contributors over this test's DAOs (mirrors
            // InventoryStatsCalculatorTest's setup)
            providerRegistry.register(ItemUsageChangeNotifier.class, ItemUsageChangeNotifier::new);
            providerRegistry.register(ItemPurchaseChangeNotifier.class, ItemPurchaseChangeNotifier::new);
            providerRegistry.register(ModuleContributorRegistry.class, ModuleContributorRegistry::new);
            ModuleContributorRegistry contributorRegistry =
                    provider.get(ModuleContributorRegistry.class);
            contributorRegistry.register(new ItemUsageDashboardContributor(provider));
            contributorRegistry.register(new ItemPurchaseDashboardContributor(provider));
            providerRegistry.register(InventoryStatsCalculator.class,
                    () -> new InventoryStatsCalculator(provider));
            // register LAST: register() eagerly resolves the singleton
            // supplier, so by the time StatisticsService's constructor asks
            // the provider for InventoryStatsCalculator it must already be
            // registered above
            providerRegistry.register(StatisticsService.class,
                    () -> new StatisticsService(provider));
        }
    }
}
