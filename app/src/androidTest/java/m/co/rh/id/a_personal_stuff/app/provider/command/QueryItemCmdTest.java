package m.co.rh.id.a_personal_stuff.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.base.test.DirectExecutorService;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Component tests for {@link QueryItemCmd#findItemStatesByBarcode} against a
 * REAL in-memory AppDatabase: the command layer owns the barcode-recall
 * contract (trim + null guard) while the DAO owns the exact-match and
 * ordering semantics, so both run through actual Room SQL instead of a
 * mocked DAO.
 */
@RunWith(AndroidJUnit4.class)
public class QueryItemCmdTest {

    private static final long BASE_MILLIS = 1_700_000_000_000L;
    private static final long STEP_MILLIS = 1_000_000L;

    private Context mContext;
    private AppDatabase mDb;
    private ExecutorService mExecutorService;
    private Provider mProvider;
    private ItemDao mItemDao;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(mContext, AppDatabase.class)
                .allowMainThreadQueries().build();
        mItemDao = mDb.itemDao();
        mExecutorService = new DirectExecutorService();
        mProvider = Provider.createProvider(mContext, new TestProviderModule());
    }

    @After
    public void tearDown() {
        if (mDb != null) {
            mDb.close();
        }
    }

    @Test
    public void findItemStatesByBarcodeReturnsExactMatchesNewestFirst() {
        ItemState older = insertItem("Older", "123", date(0));
        ItemState newer = insertItem("Newer", "123", date(1));
        insertItem("Prefix", "0123", date(2));
        insertItem("Extended", "1234", date(3));

        List<ItemState> result = queryItemCmd().findItemStatesByBarcode("123").blockingGet();

        // exact "=" match only, ordered created DESC (the command layer owns
        // the DAO's ordering here)
        assertEquals(2, result.size());
        assertEquals(newer.getItemId(), result.get(0).getItemId());
        assertEquals(older.getItemId(), result.get(1).getItemId());
    }

    @Test
    public void findItemStatesByBarcodeTrimsPaddedSearch() {
        ItemState beans = insertItem("Beans", "123", date(0));

        // the scan pipelines hand the barcode over as-is: padded whitespace
        // must still match the stored value
        List<ItemState> result = queryItemCmd().findItemStatesByBarcode("  123  ").blockingGet();

        assertEquals(1, result.size());
        assertEquals(beans.getItemId(), result.get(0).getItemId());
    }

    @Test
    public void findItemStatesByBarcodeWithNoMatchReturnsEmptyList() {
        insertItem("Beans", "123", date(0));

        assertTrue(queryItemCmd().findItemStatesByBarcode("999").blockingGet().isEmpty());
    }

    @Test
    public void findItemStatesByBarcodeWithNullReturnsEmptyList() {
        insertItem("Beans", "123", date(0));

        assertTrue(queryItemCmd().findItemStatesByBarcode(null).blockingGet().isEmpty());
    }

    private QueryItemCmd queryItemCmd() {
        return new QueryItemCmd(mProvider);
    }

    private ItemState insertItem(String name, String barcode, Date created) {
        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = name;
        item.amount = 1;
        item.barcode = barcode;
        item.createdDateTime = created;
        item.updatedDateTime = created;
        itemState.updateItem(item);
        mItemDao.insertItem(itemState);
        return itemState;
    }

    private static Date date(int rank) {
        return new Date(BASE_MILLIS + rank * STEP_MILLIS);
    }

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            // the direct executor runs the command inline on the calling
            // thread so the Single completes deterministically without latches
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemDao.class, () -> mItemDao);
        }
    }
}
