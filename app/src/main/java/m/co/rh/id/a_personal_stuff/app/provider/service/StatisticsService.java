package m.co.rh.id.a_personal_stuff.app.provider.service;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import m.co.rh.id.a_personal_stuff.app.provider.component.InventoryStatsCalculator;
import m.co.rh.id.a_personal_stuff.app.ui.model.StatisticsData;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseMonthTotal;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageTotal;
import m.co.rh.id.aprovider.Provider;

/**
 * Computes the statistics-page aggregates: monthly spend (bar chart),
 * most-consumed items (ranked list) and the inventory summary (total value
 * + expired count). All reads are blocking Room queries — callers must
 * invoke {@link #computeStatistics()} on a background thread (the injected
 * {@link StatisticsCmd} owns the thread choice).
 * <p>
 * Stateless like {@link ExportService}: every run keeps its intermediate
 * state in locals, the only fields are the injected collaborators.
 */
public class StatisticsService {

    /** Months shown by the spend chart (continuous, gap-filled window). */
    private static final int MONTH_WINDOW = 12;
    /** Rank-list cap for the most-consumed items. */
    private static final int MOST_CONSUMED_LIMIT = 5;

    private final ItemDao mItemDao;
    private final ItemPurchaseDao mItemPurchaseDao;
    private final ItemUsageDao mItemUsageDao;
    private final InventoryStatsCalculator mInventoryStatsCalculator;

    public StatisticsService(Provider provider) {
        mItemDao = provider.get(ItemDao.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mInventoryStatsCalculator = provider.get(InventoryStatsCalculator.class);
    }

    /**
     * Blocking aggregation over the base + purchase + usage databases.
     * Returns the fully populated {@link StatisticsData}.
     */
    public StatisticsData computeStatistics() {
        Date now = new Date();
        StatisticsData statisticsData = new StatisticsData();
        computeMonthlySpend(statisticsData, now);
        computeMostConsumed(statisticsData);
        statisticsData.inventoryValue = mItemDao.sumInventoryValue();
        // the dashboard's in-stock rule (countExpiredInStock reuses the
        // calculator's stock filter + expiry split) so the statistics page
        // can never diverge from the dashboard's expired count
        statisticsData.expiredCount = mInventoryStatsCalculator.countExpiredInStock(now);
        return statisticsData;
    }

    /**
     * Fills {@link StatisticsData#monthlySpend} as a CONTINUOUS ascending
     * 12-month series: the DAO only returns months that have purchases
     * (newest-first, LIMIT 12), so the missing months inside the window are
     * gap-filled with 0. Month keys are generated with {@link Calendar} in
     * the device default timezone — matching the DAO's localtime bucketing.
     */
    private void computeMonthlySpend(StatisticsData statisticsData, Date now) {
        // future-dated purchases fall outside the generated 12-month window
        // (which always ends at the current month) and are intentionally not
        // charted — their bucket is simply absent from the series
        List<ItemPurchaseMonthTotal> monthTotals = mItemPurchaseDao.sumCostGroupByMonth();
        Map<String, Double> totalByMonth = new HashMap<>();
        for (ItemPurchaseMonthTotal monthTotal : monthTotals) {
            // undated purchases (null purchase_date_time) produce a null
            // month bucket — they belong to no displayable month
            if (monthTotal.month != null) {
                totalByMonth.put(monthTotal.month, monthTotal.total);
            }
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.MONTH, -(MONTH_WINDOW - 1));
        SimpleDateFormat monthKeyFormat = new SimpleDateFormat("yyyy-MM", Locale.US);
        for (int i = 0; i < MONTH_WINDOW; i++) {
            StatisticsData.MonthlySpend monthlySpend = new StatisticsData.MonthlySpend();
            monthlySpend.month = monthKeyFormat.format(calendar.getTime());
            Double total = totalByMonth.get(monthlySpend.month);
            monthlySpend.total = total != null ? total : 0d;
            statisticsData.monthlySpend.add(monthlySpend);
            calendar.add(Calendar.MONTH, 1);
        }
    }

    /**
     * Fills {@link StatisticsData#mostConsumedItems}: the usage module's
     * Σamount-per-item aggregate merged with item names (resolved via
     * ItemDao), sorted by total amount descending and capped at
     * {@link #MOST_CONSUMED_LIMIT}. Items with no usage records are simply
     * absent; an aggregate whose item no longer exists is skipped
     * (defensive — cascade deletes remove usages with their item).
     */
    private void computeMostConsumed(StatisticsData statisticsData) {
        List<ItemUsageTotal> usageTotals = mItemUsageDao.sumAmountGroupByItem();
        usageTotals.sort((left, right) -> Integer.compare(right.total, left.total));
        int limit = Math.min(MOST_CONSUMED_LIMIT, usageTotals.size());
        for (int i = 0; i < limit; i++) {
            ItemUsageTotal usageTotal = usageTotals.get(i);
            Item item = mItemDao.findItemById(usageTotal.itemId);
            if (item == null) {
                continue;
            }
            StatisticsData.MostConsumedItem mostConsumedItem =
                    new StatisticsData.MostConsumedItem();
            mostConsumedItem.itemId = usageTotal.itemId;
            mostConsumedItem.itemName = item.name;
            mostConsumedItem.totalAmount = usageTotal.total;
            statisticsData.mostConsumedItems.add(mostConsumedItem);
        }
    }
}
