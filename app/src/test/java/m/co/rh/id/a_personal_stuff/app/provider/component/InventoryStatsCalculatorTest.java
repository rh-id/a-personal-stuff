package m.co.rh.id.a_personal_stuff.app.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseTotal;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchaseDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageTotal;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageDashboardContributor;
import m.co.rh.id.aprovider.Provider;

public class InventoryStatsCalculatorTest {

    private static final long NOW_MILLIS = 1700000000000L;
    private static final long MILLIS_PER_DAY = 86400000L;

    private Provider mProvider;
    private ItemDao mItemDao;
    private ItemUsageDao mItemUsageDao;
    private ItemPurchaseDao mItemPurchaseDao;
    private InventoryStatsCalculator mCalculator;

    @Before
    public void setUp() {
        mProvider = mock(Provider.class);
        mItemDao = mock(ItemDao.class);
        mItemUsageDao = mock(ItemUsageDao.class);
        mItemPurchaseDao = mock(ItemPurchaseDao.class);
        when(mProvider.get(ItemDao.class)).thenReturn(mItemDao);
        when(mProvider.get(ItemUsageDao.class)).thenReturn(mItemUsageDao);
        when(mProvider.get(ItemPurchaseDao.class)).thenReturn(mItemPurchaseDao);
        // the calculator consumes the signed per-item stock deltas through
        // the contributor contract: register the REAL usage/purchase
        // dashboard contributors over the mocked DAOs (usage −Σ, purchase +Σ)
        BackupContributorRegistry contributorRegistry = new BackupContributorRegistry();
        contributorRegistry.register(new ItemUsageDashboardContributor(mProvider));
        contributorRegistry.register(new ItemPurchaseDashboardContributor(mProvider));
        when(mProvider.get(BackupContributorRegistry.class)).thenReturn(contributorRegistry);
        // defaults for the collection-returning DAO methods so compute() never
        // sees a null list; individual tests override what they care about
        when(mItemUsageDao.sumAmountGroupByItem()).thenReturn(new ArrayList<>());
        when(mItemPurchaseDao.sumAmountGroupByItem()).thenReturn(new ArrayList<>());
        when(mItemDao.findItemsExpiringBefore(any(Date.class))).thenReturn(new ArrayList<>());
        when(mItemDao.findItemsWithMinAmount()).thenReturn(new ArrayList<>());
        mCalculator = new InventoryStatsCalculator(mProvider);
    }

    @Test
    public void computeWithEmptyDaosYieldsZeroedSummary() {
        when(mItemDao.countItems()).thenReturn(0);
        // Mockito defaults a Double-returning method to 0.0, so null must be
        // stubbed explicitly to represent an inventory with no valued items
        when(mItemDao.sumInventoryValue()).thenReturn(null);

        InventoryStatsCalculator.AlertSummary alertSummary = mCalculator.compute(new Date(NOW_MILLIS), 7);

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
        when(mItemDao.countItems()).thenReturn(2);
        double expectedValue = 3 * 2.50 + 4 * 0.99;
        when(mItemDao.sumInventoryValue()).thenReturn(expectedValue);

        InventoryStatsCalculator.AlertSummary alertSummary = mCalculator.compute(new Date(NOW_MILLIS), 7);

        assertEquals(2, alertSummary.totalItems);
        assertEquals(expectedValue, alertSummary.inventoryValue, 0.0001);
    }

    @Test
    public void expiryBoundarySplitsExpiredAndExpiringWithinLeadDays() {
        // DAO returns all in-stock items expiring at or before now+leadDays
        // (open lower bound): items at or before now are expired, the rest
        // are expiring soon
        Date now = new Date(NOW_MILLIS);
        when(mItemDao.countItems()).thenReturn(4);
        when(mItemDao.findItemsExpiringBefore(any(Date.class))).thenReturn(Arrays.asList(
                itemWithExpiry(1L, new Date(NOW_MILLIS - MILLIS_PER_DAY)), // past → expired
                itemWithExpiry(2L, now),                                   // exactly now → expired
                itemWithExpiry(3L, new Date(NOW_MILLIS + 3 * MILLIS_PER_DAY)), // within lead → expiring
                itemWithExpiry(4L, new Date(NOW_MILLIS + 7 * MILLIS_PER_DAY))  // at lead end → expiring
        ));

        InventoryStatsCalculator.AlertSummary alertSummary = mCalculator.compute(now, 7);

        assertEquals(4, alertSummary.expiringItems.size());
        assertEquals(2, alertSummary.expiredCount);
        assertEquals(2, alertSummary.expiringCount);
    }

    @Test
    public void lowStockUsesDerivedRemainingStock() {
        // remaining = amount − Σusage.amount + Σpurchase.amount, low when <= minAmount
        Date now = new Date(NOW_MILLIS);
        Item notLowStock = new Item();
        notLowStock.id = 1L;
        notLowStock.amount = 10;
        notLowStock.minAmount = 5; // remaining 10 − 4 + 1 = 7 > 5 → not low
        Item lowStock = new Item();
        lowStock.id = 2L;
        lowStock.amount = 5;
        lowStock.minAmount = 5; // remaining 5 − 2 + 0 = 3 <= 5 → low

        when(mItemDao.findItemsWithMinAmount()).thenReturn(new ArrayList<>(
                Arrays.asList(notLowStock, lowStock)));
        when(mItemUsageDao.sumAmountGroupByItem()).thenReturn(totals(
                usageTotal(1L, 4),
                usageTotal(2L, 2)));
        when(mItemPurchaseDao.sumAmountGroupByItem()).thenReturn(totals(
                purchaseTotal(1L, 1)));

        InventoryStatsCalculator.AlertSummary alertSummary = mCalculator.compute(now, 7);

        assertEquals(1, alertSummary.lowStockItems.size());
        assertEquals(Long.valueOf(2L), alertSummary.lowStockItems.get(0).id);
    }

    @Test
    public void findAlertItemIdsDeduplicatesExpiredAndLowStock() {
        // item 1 is both expired and low on stock: it must appear exactly
        // once when both alert types are requested
        Date now = new Date(NOW_MILLIS);
        Item both = itemWithExpiry(1L, new Date(NOW_MILLIS - MILLIS_PER_DAY));
        both.amount = 5;
        both.minAmount = 5; // remaining 5 − 2 = 3 <= 5 → low stock
        when(mItemDao.findItemsExpiringBefore(any(Date.class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(both)));
        when(mItemDao.findItemsWithMinAmount())
                .thenReturn(new ArrayList<>(Collections.singletonList(both)));
        when(mItemUsageDao.sumAmountGroupByItem()).thenReturn(totals(usageTotal(1L, 2)));

        List<Long> alertItemIds = mCalculator.findAlertItemIds(
                EnumSet.of(InventoryStatsCalculator.AlertType.EXPIRED,
                        InventoryStatsCalculator.AlertType.LOW_STOCK),
                now, 7);

        assertEquals(1, alertItemIds.size());
        assertEquals(Long.valueOf(1L), alertItemIds.get(0));
    }

    @Test
    public void findAlertItemIdsCountsExpiryAtNowAsExpired() {
        // the expired/expiring split uses !expiredDateTime.after(now), so an
        // item whose expiry is exactly now is EXPIRED, not EXPIRING_SOON
        Date now = new Date(NOW_MILLIS);
        when(mItemDao.findItemsExpiringBefore(any(Date.class))).thenReturn(new ArrayList<>(
                Collections.singletonList(itemWithExpiry(1L, now))));

        List<Long> expired = mCalculator.findAlertItemIds(
                EnumSet.of(InventoryStatsCalculator.AlertType.EXPIRED), now, 7);
        List<Long> expiringSoon = mCalculator.findAlertItemIds(
                EnumSet.of(InventoryStatsCalculator.AlertType.EXPIRING_SOON), now, 7);

        assertEquals(1, expired.size());
        assertEquals(Long.valueOf(1L), expired.get(0));
        assertTrue("an item whose expiry is exactly now must not be expiring soon",
                expiringSoon.isEmpty());
    }

    @Test
    public void findAlertItemIdsReturnsOnlyRequestedTypes() {
        // requesting only LOW_STOCK must not leak expiring-only items
        Date now = new Date(NOW_MILLIS);
        Item expiringOnly = itemWithExpiry(1L, new Date(NOW_MILLIS + 3 * MILLIS_PER_DAY));
        Item lowStockOnly = new Item();
        lowStockOnly.id = 2L;
        lowStockOnly.amount = 1;
        lowStockOnly.minAmount = 3; // remaining 1 <= 3 → low stock
        when(mItemDao.findItemsExpiringBefore(any(Date.class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(expiringOnly)));
        when(mItemDao.findItemsWithMinAmount())
                .thenReturn(new ArrayList<>(Collections.singletonList(lowStockOnly)));

        List<Long> alertItemIds = mCalculator.findAlertItemIds(
                EnumSet.of(InventoryStatsCalculator.AlertType.LOW_STOCK), now, 7);

        assertEquals(1, alertItemIds.size());
        assertEquals(Long.valueOf(2L), alertItemIds.get(0));
    }

    @Test
    public void findAlertItemIdsExcludesOutOfStockItemsFromExpiry() {
        // a fully used-up item (remaining stock <= 0) is not an expiry alert
        // regardless of its expiry date, matching the dashboard counts
        Date now = new Date(NOW_MILLIS);
        Item usedUp = itemWithExpiry(1L, new Date(NOW_MILLIS - MILLIS_PER_DAY));
        usedUp.amount = 5;
        when(mItemDao.findItemsExpiringBefore(any(Date.class)))
                .thenReturn(new ArrayList<>(Collections.singletonList(usedUp)));
        when(mItemUsageDao.sumAmountGroupByItem()).thenReturn(totals(usageTotal(1L, 5)));

        Set<InventoryStatsCalculator.AlertType> types =
                EnumSet.of(InventoryStatsCalculator.AlertType.EXPIRED,
                        InventoryStatsCalculator.AlertType.EXPIRING_SOON);
        List<Long> alertItemIds = mCalculator.findAlertItemIds(types, now, 7);

        assertTrue(alertItemIds.isEmpty());
    }

    private static Item itemWithExpiry(long id, Date expiredDateTime) {
        Item item = new Item();
        item.id = id;
        item.amount = 1;
        item.expiredDateTime = expiredDateTime;
        return item;
    }

    private static ItemUsageTotal usageTotal(long itemId, int total) {
        ItemUsageTotal itemUsageTotal = new ItemUsageTotal();
        itemUsageTotal.itemId = itemId;
        itemUsageTotal.total = total;
        return itemUsageTotal;
    }

    private static ItemPurchaseTotal purchaseTotal(long itemId, int total) {
        ItemPurchaseTotal itemPurchaseTotal = new ItemPurchaseTotal();
        itemPurchaseTotal.itemId = itemId;
        itemPurchaseTotal.total = total;
        return itemPurchaseTotal;
    }

    private static List<ItemUsageTotal> totals(ItemUsageTotal... entries) {
        return new ArrayList<>(Arrays.asList(entries));
    }

    private static List<ItemPurchaseTotal> totals(ItemPurchaseTotal... entries) {
        return new ArrayList<>(Arrays.asList(entries));
    }
}
