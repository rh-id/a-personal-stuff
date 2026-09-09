package m.co.rh.id.a_personal_stuff.app.provider.component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseTotal;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageTotal;
import m.co.rh.id.aprovider.Provider;

/**
 * Computes the shared inventory aggregates used by the dashboard cards and
 * the inventory alert digest notification. All reads are blocking Room
 * queries — callers must invoke {@link #compute(Date, int)} on a background
 * thread.
 */
public class InventoryStatsCalculator {

    private static final long MILLIS_PER_DAY = 86400000L;

    private ItemDao mItemDao;
    private ItemUsageDao mItemUsageDao;
    private ItemPurchaseDao mItemPurchaseDao;

    public InventoryStatsCalculator(Provider provider) {
        mItemDao = provider.get(ItemDao.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
    }

    public AlertSummary compute(Date now, int leadDays) {
        AlertSummary alertSummary = new AlertSummary();
        Date leadEnd = new Date(now.getTime() + leadDays * MILLIS_PER_DAY);
        alertSummary.totalItems = mItemDao.countItems();
        alertSummary.inventoryValue = mItemDao.sumInventoryValue();
        // the derived remaining stock spans the usage and purchase databases,
        // so the totals are loaded once here and shared by both the expiry
        // filter and the low-stock check
        Map<Long, Integer> usageTotals = buildUsageTotals();
        Map<Long, Integer> purchaseTotals = buildPurchaseTotals();
        alertSummary.expiringItems = filterItemsInStock(
                mItemDao.findItemsExpiringBefore(leadEnd),
                usageTotals, purchaseTotals);
        computeExpiryCounts(alertSummary, now);
        computeLowStock(alertSummary, usageTotals, purchaseTotals);
        return alertSummary;
    }

    private Map<Long, Integer> buildUsageTotals() {
        Map<Long, Integer> usageTotals = new HashMap<>();
        for (ItemUsageTotal itemUsageTotal : mItemUsageDao.sumAmountGroupByItem()) {
            usageTotals.put(itemUsageTotal.itemId, itemUsageTotal.total);
        }
        return usageTotals;
    }

    private Map<Long, Integer> buildPurchaseTotals() {
        Map<Long, Integer> purchaseTotals = new HashMap<>();
        for (ItemPurchaseTotal itemPurchaseTotal : mItemPurchaseDao.sumAmountGroupByItem()) {
            purchaseTotals.put(itemPurchaseTotal.itemId, itemPurchaseTotal.total);
        }
        return purchaseTotals;
    }

    /**
     * Derived remaining stock: amount − Σusage + Σpurchase, matching the
     * item card badge. Items with no usage/purchase records simply have
     * full stock (missing totals default to 0).
     */
    private static int computeRemainingStock(Item item,
                                             Map<Long, Integer> usageTotals,
                                             Map<Long, Integer> purchaseTotals) {
        Integer usage = usageTotals.get(item.id);
        Integer purchase = purchaseTotals.get(item.id);
        return item.amount
                - (usage != null ? usage : 0)
                + (purchase != null ? purchase : 0);
    }

    /**
     * Removes items whose derived remaining stock is exhausted (<= 0):
     * a fully used-up item is no longer an expiry alert regardless of
     * its expiry date.
     */
    private static List<Item> filterItemsInStock(List<Item> items,
                                                 Map<Long, Integer> usageTotals,
                                                 Map<Long, Integer> purchaseTotals) {
        List<Item> inStockItems = new ArrayList<>(items.size());
        for (Item item : items) {
            if (computeRemainingStock(item, usageTotals, purchaseTotals) > 0) {
                inStockItems.add(item);
            }
        }
        return inStockItems;
    }

    /**
     * Derives the expired/expiring counts from the stock-filtered
     * {@link AlertSummary#expiringItems} so counts and samples always
     * agree. The list has an open lower bound (includes expired):
     * items at or before now count as expired, the rest as expiring soon.
     */
    private static void computeExpiryCounts(AlertSummary alertSummary, Date now) {
        int expiredCount = 0;
        for (Item item : alertSummary.expiringItems) {
            if (!item.expiredDateTime.after(now)) {
                expiredCount++;
            }
        }
        alertSummary.expiredCount = expiredCount;
        alertSummary.expiringCount = alertSummary.expiringItems.size() - expiredCount;
    }

    /**
     * Low stock uses the derived remaining stock (amount − Σusage + Σpurchase,
     * matching the item card badge) instead of the raw amount vs min_amount
     * comparison.
     */
    private void computeLowStock(AlertSummary alertSummary,
                                 Map<Long, Integer> usageTotals,
                                 Map<Long, Integer> purchaseTotals) {
        List<Item> lowStockItems = new ArrayList<>();
        List<Item> candidates = mItemDao.findItemsWithMinAmount();
        for (Item item : candidates) {
            int remaining = computeRemainingStock(item, usageTotals, purchaseTotals);
            if (remaining <= item.minAmount) {
                lowStockItems.add(item);
            }
        }
        alertSummary.lowStockItems = lowStockItems;
    }

    public static class AlertSummary {
        public int totalItems;
        /** Nullable — null when the inventory has no valued items. */
        public Double inventoryValue;
        /** In-stock items whose expired_date_time is at or before now. */
        public int expiredCount;
        /** In-stock items expiring after now and at or before now + leadDays. */
        public int expiringCount;
        /**
         * All in-stock items expiring at or before now + leadDays, expiry ASC
         * (open lower bound — includes expired). In-stock means the derived
         * remaining stock (amount − Σusage + Σpurchase) is positive; fully
         * used-up items are excluded even if their expiry date has passed.
         */
        public List<Item> expiringItems = new ArrayList<>();
        /** All items low on derived remaining stock, in insertion (id) order. */
        public List<Item> lowStockItems = new ArrayList<>();
    }
}
