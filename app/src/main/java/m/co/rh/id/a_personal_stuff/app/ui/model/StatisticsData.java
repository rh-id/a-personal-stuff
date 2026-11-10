package m.co.rh.id.a_personal_stuff.app.ui.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates shown by the statistics page: the continuous last-12-month
 * spend series (gap-filled so months without purchases carry 0), the
 * top most-consumed items ranked by total used amount, and the inventory
 * summary (total value + expired count).
 */
public class StatisticsData {

    /**
     * Spend per month over the last 12 months, ascending (oldest first).
     * Every month key is present — months without purchases carry 0.
     */
    public List<MonthlySpend> monthlySpend = new ArrayList<>();

    /**
     * Most-consumed items ranked by total used amount (descending), capped
     * at the top 5; items with no usage records are simply absent.
     */
    public List<MostConsumedItem> mostConsumedItems = new ArrayList<>();

    /** Nullable — null when the inventory has no valued items. */
    public Double inventoryValue;

    /**
     * Items whose expired_date_time is at or before now AND whose derived
     * remaining stock is still positive — the same in-stock rule the
     * dashboard's expired count uses (a fully used-up item is not counted
     * as expired even when its expiry date has passed).
     */
    public int expiredCount;

    /**
     * True when there is nothing worth showing at all: no purchases in the
     * window, no usage records, no valued inventory AND no expired items.
     * Valued inventory or expired items alone are enough to surface the
     * summary cards, so they keep the page non-empty.
     */
    public boolean isEmpty() {
        boolean anySpend = false;
        for (MonthlySpend monthlySpend : monthlySpend) {
            if (monthlySpend.total > 0) {
                anySpend = true;
                break;
            }
        }
        if (anySpend || !mostConsumedItems.isEmpty()) {
            return false;
        }
        return inventoryValue == null && expiredCount == 0;
    }

    public static class MonthlySpend {
        /** Device-local month key, 'yyyy-MM'. */
        public String month;
        /** Total spend over the month (0 when the month had no purchases). */
        public double total;
    }

    public static class MostConsumedItem {
        public long itemId;
        /** Resolved via ItemDao; never null. */
        public String itemName;
        /** Total amount used across all usage records. */
        public int totalAmount;
    }
}
