package m.co.rh.id.a_personal_stuff.item_purchase.dao;

import static org.junit.Assert.assertEquals;

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

import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseMonthTotal;
import m.co.rh.id.a_personal_stuff.item_purchase.room.ItemPurchaseDatabase;

/**
 * DAO tests for the monthly-spend aggregation ({@link ItemPurchaseDao#sumCostGroupByMonth()}).
 * Seeds inline entities (the shared {@code TestSeeds} factory lives in :app's
 * androidTest and is NOT visible to this module). Expectations are derived
 * from {@link Calendar} in the DEVICE-DEFAULT timezone so the tests assert
 * the device-local bucketing (never UTC) no matter where they run.
 */
@RunWith(AndroidJUnit4.class)
public class ItemPurchaseDaoTest {

    private Context mContext;
    private ItemPurchaseDatabase mDb;
    private ItemPurchaseDao mDao;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(mContext, ItemPurchaseDatabase.class)
                .allowMainThreadQueries().build();
        mDao = mDb.itemPurchaseDao();
    }

    @After
    public void tearDown() {
        if (mDb != null) {
            mDb.close();
        }
    }

    @Test
    public void purchasesInDifferentMonthsLandInCorrectBuckets() {
        // wall-clock construction via Calendar: these are local-tz dates, so
        // the expected buckets below are correct in every timezone
        insertPurchase(localDate(2024, Calendar.JANUARY, 15), "10.00");
        insertPurchase(localDate(2024, Calendar.MARCH, 5), "5.50");

        List<ItemPurchaseMonthTotal> monthTotals = mDao.sumCostGroupByMonth();

        assertEquals(2, monthTotals.size());
        // newest month first (ORDER BY month DESC)
        assertEquals("2024-03", monthTotals.get(0).month);
        assertEquals(5.50, monthTotals.get(0).total, 0.0001);
        assertEquals("2024-01", monthTotals.get(1).month);
        assertEquals(10.00, monthTotals.get(1).total, 0.0001);
    }

    @Test
    public void twoPurchasesSameMonthSum() {
        insertPurchase(localDate(2024, Calendar.FEBRUARY, 3), "3.25");
        insertPurchase(localDate(2024, Calendar.FEBRUARY, 27), "4.75");

        List<ItemPurchaseMonthTotal> monthTotals = mDao.sumCostGroupByMonth();

        assertEquals(1, monthTotals.size());
        assertEquals("2024-02", monthTotals.get(0).month);
        assertEquals(8.00, monthTotals.get(0).total, 0.0001);
    }

    @Test
    public void bucketsFollowDeviceLocalMonthsAroundMonthStart() {
        // timezone edge: start of the CURRENT local month truncated to
        // midnight; one purchase 30 minutes BEFORE it (still previous local
        // month) and one 30 minutes AFTER it (already current local month).
        // The expected keys are computed with SimpleDateFormat over the same
        // device-local instants, so this passes in every timezone — a naive
        // UTC strftime bucketing would fail for any device whose local
        // midnight differs from UTC midnight.
        Calendar monthStart = Calendar.getInstance();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        monthStart.set(Calendar.HOUR_OF_DAY, 0);
        monthStart.set(Calendar.MINUTE, 0);
        monthStart.set(Calendar.SECOND, 0);
        monthStart.set(Calendar.MILLISECOND, 0);
        long halfHourMillis = 30 * 60 * 1000L;
        Date afterMonthStart = new Date(monthStart.getTimeInMillis() + halfHourMillis);
        Date beforeMonthStart = new Date(monthStart.getTimeInMillis() - halfHourMillis);
        SimpleDateFormat monthKeyFormat = new SimpleDateFormat("yyyy-MM", Locale.US);
        String expectedCurrentMonth = monthKeyFormat.format(afterMonthStart);
        String expectedPreviousMonth = monthKeyFormat.format(beforeMonthStart);

        insertPurchase(afterMonthStart, "2.00");
        insertPurchase(beforeMonthStart, "1.00");

        List<ItemPurchaseMonthTotal> monthTotals = mDao.sumCostGroupByMonth();

        assertEquals(2, monthTotals.size());
        // newest month first (ORDER BY month DESC)
        assertEquals(expectedCurrentMonth, monthTotals.get(0).month);
        assertEquals(2.00, monthTotals.get(0).total, 0.0001);
        assertEquals(expectedPreviousMonth, monthTotals.get(1).month);
        assertEquals(1.00, monthTotals.get(1).total, 0.0001);
    }

    private Date localDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, 12, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private void insertPurchase(Date purchaseDateTime, String cost) {
        ItemPurchase itemPurchase = new ItemPurchase();
        itemPurchase.description = "purchase";
        itemPurchase.amount = 1;
        itemPurchase.cost = new BigDecimal(cost);
        itemPurchase.purchaseDateTime = purchaseDateTime;
        itemPurchase.createdDateTime = purchaseDateTime;
        mDao.insert(itemPurchase);
    }
}
