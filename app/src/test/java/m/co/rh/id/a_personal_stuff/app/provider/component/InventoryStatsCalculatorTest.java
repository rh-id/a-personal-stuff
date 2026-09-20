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
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseTotal;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageTotal;
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
