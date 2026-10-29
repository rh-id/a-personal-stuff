package m.co.rh.id.a_personal_stuff.item_purchase.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;
import m.co.rh.id.a_personal_stuff.item_purchase.room.ItemPurchaseDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

@RunWith(AndroidJUnit4.class)
public class ItemPurchaseEventHandlerTest {

    private Context mContext;
    private ItemPurchaseDatabase mDb;
    private ExecutorService mExecutorService;
    private ItemChangeNotifier mNotifier;
    private Provider mProvider;
    private ItemPurchaseEventHandler mHandler;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(mContext, ItemPurchaseDatabase.class)
                .allowMainThreadQueries().build();
        mExecutorService = Executors.newSingleThreadExecutor();
        mNotifier = new ItemChangeNotifier();
        mProvider = Provider.createProvider(mContext, new TestProviderModule());
        mHandler = new ItemPurchaseEventHandler(mProvider);
    }

    @After
    public void tearDown() {
        if (mHandler != null) {
            mHandler.dispose(mContext);
        }
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
        }
        if (mDb != null) {
            mDb.close();
        }
    }

    @Test
    public void deletedItemEventDeletesPurchaseAndImageRows() throws Exception {
        long deletedItemId = 1L;
        long survivingItemId = 2L;
        Date now = new Date();

        ItemPurchase deletedPurchase = new ItemPurchase();
        deletedPurchase.itemId = deletedItemId;
        deletedPurchase.description = "deleted purchase";
        deletedPurchase.amount = 1;
        deletedPurchase.cost = new BigDecimal("9.99");
        deletedPurchase.purchaseDateTime = now;
        deletedPurchase.createdDateTime = now;
        long deletedPurchaseId = mDb.itemPurchaseDao().insert(deletedPurchase);

        ItemPurchaseImage deletedImage = new ItemPurchaseImage();
        deletedImage.itemPurchaseId = deletedPurchaseId;
        deletedImage.fileName = "deleted_purchase.jpg";
        deletedImage.createdDateTime = now;
        mDb.itemPurchaseDao().insert(deletedImage);

        ItemPurchase survivingPurchase = new ItemPurchase();
        survivingPurchase.itemId = survivingItemId;
        survivingPurchase.description = "surviving purchase";
        survivingPurchase.amount = 1;
        survivingPurchase.cost = new BigDecimal("4.99");
        survivingPurchase.purchaseDateTime = now;
        survivingPurchase.createdDateTime = now;
        long survivingPurchaseId = mDb.itemPurchaseDao().insert(survivingPurchase);

        ItemPurchaseImage survivingImage = new ItemPurchaseImage();
        survivingImage.itemPurchaseId = survivingPurchaseId;
        survivingImage.fileName = "surviving_purchase.jpg";
        survivingImage.createdDateTime = now;
        mDb.itemPurchaseDao().insert(survivingImage);

        mNotifier.itemDeleted(deletedItemState(deletedItemId));

        // the handler processes the event on its executor thread, so poll
        assertTrue(await(() -> mDb.itemPurchaseDao().findItemPurchaseByItemId(deletedItemId).isEmpty()));
        // the second-level image row must be gone with its parent
        assertNull(mDb.itemPurchaseDao().findItemPurchaseImageByFileName("deleted_purchase.jpg"));
        // other items' rows survive untouched
        assertEquals(1, mDb.itemPurchaseDao().findItemPurchaseByItemId(survivingItemId).size());
        assertNotNull(mDb.itemPurchaseDao().findItemPurchaseImageByFileName("surviving_purchase.jpg"));
    }

    private ItemState deletedItemState(long itemId) {
        Item item = new Item();
        item.id = itemId;
        item.name = "deleted item " + itemId;
        ItemState itemState = new ItemState();
        itemState.updateItem(item);
        return itemState;
    }

    /**
     * Polls until the condition holds or the timeout elapses.
     */
    private boolean await(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && !condition.getAsBoolean()) {
            Thread.sleep(50);
        }
        return condition.getAsBoolean();
    }

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, this::getLogger);
            providerRegistry.register(ItemChangeNotifier.class, () -> mNotifier);
            providerRegistry.register(ItemPurchaseDao.class, () -> mDb.itemPurchaseDao());
        }

        private ILogger getLogger() {
            return new ILogger() {
                @Override
                public void v(String tag, String message) {
                }

                @Override
                public void v(String tag, String message, Throwable throwable) {
                }

                @Override
                public void d(String tag, String message) {
                }

                @Override
                public void d(String tag, String message, Throwable throwable) {
                }

                @Override
                public void i(String tag, String message) {
                }

                @Override
                public void i(String tag, String message, Throwable throwable) {
                }

                @Override
                public void w(String tag, String message) {
                }

                @Override
                public void w(String tag, String message, Throwable throwable) {
                }

                @Override
                public void e(String tag, String message) {
                }

                @Override
                public void e(String tag, String message, Throwable throwable) {
                }

                @Override
                public void setLogLevel(int logLevel) {
                }
            };
        }
    }
}
