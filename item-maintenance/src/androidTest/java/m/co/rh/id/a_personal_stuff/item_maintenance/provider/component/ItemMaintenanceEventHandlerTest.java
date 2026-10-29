package m.co.rh.id.a_personal_stuff.item_maintenance.provider.component;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.room.ItemMaintenanceDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

@RunWith(AndroidJUnit4.class)
public class ItemMaintenanceEventHandlerTest {

    private Context mContext;
    private ItemMaintenanceDatabase mDb;
    private ExecutorService mExecutorService;
    private ItemChangeNotifier mNotifier;
    private Provider mProvider;
    private ItemMaintenanceEventHandler mHandler;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(mContext, ItemMaintenanceDatabase.class)
                .allowMainThreadQueries().build();
        mExecutorService = Executors.newSingleThreadExecutor();
        mNotifier = new ItemChangeNotifier();
        mProvider = Provider.createProvider(mContext, new TestProviderModule());
        mHandler = new ItemMaintenanceEventHandler(mProvider);
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
    public void deletedItemEventDeletesMaintenanceAndImageRows() throws Exception {
        long deletedItemId = 1L;
        long survivingItemId = 2L;
        Date now = new Date();

        ItemMaintenance deletedMaintenance = new ItemMaintenance();
        deletedMaintenance.itemId = deletedItemId;
        deletedMaintenance.description = "deleted maintenance";
        deletedMaintenance.cost = new BigDecimal("19.99");
        deletedMaintenance.maintenanceDateTime = now;
        deletedMaintenance.createdDateTime = now;
        long deletedMaintenanceId = mDb.itemMaintenanceDao().insert(deletedMaintenance);

        ItemMaintenanceImage deletedImage = new ItemMaintenanceImage();
        deletedImage.itemMaintenanceId = deletedMaintenanceId;
        deletedImage.fileName = "deleted_maintenance.jpg";
        deletedImage.createdDateTime = now;
        mDb.itemMaintenanceDao().insert(deletedImage);

        ItemMaintenance survivingMaintenance = new ItemMaintenance();
        survivingMaintenance.itemId = survivingItemId;
        survivingMaintenance.description = "surviving maintenance";
        survivingMaintenance.cost = new BigDecimal("14.99");
        survivingMaintenance.maintenanceDateTime = now;
        survivingMaintenance.createdDateTime = now;
        long survivingMaintenanceId = mDb.itemMaintenanceDao().insert(survivingMaintenance);

        ItemMaintenanceImage survivingImage = new ItemMaintenanceImage();
        survivingImage.itemMaintenanceId = survivingMaintenanceId;
        survivingImage.fileName = "surviving_maintenance.jpg";
        survivingImage.createdDateTime = now;
        mDb.itemMaintenanceDao().insert(survivingImage);

        mNotifier.itemDeleted(deletedItemState(deletedItemId));

        // the handler processes the event on its executor thread, so poll
        assertTrue(await(() -> findMaintenancesFor(deletedItemId).isEmpty()));
        // the second-level image row must be gone with its parent
        assertNull(mDb.itemMaintenanceDao().findItemMaintenanceImageByFileName("deleted_maintenance.jpg"));
        // other items' rows survive untouched
        assertEquals(1, findMaintenancesFor(survivingItemId).size());
        assertNotNull(mDb.itemMaintenanceDao().findItemMaintenanceImageByFileName("surviving_maintenance.jpg"));
    }

    // the DAO has no public per-item finder, so filter the public full-table
    // query here
    private List<ItemMaintenance> findMaintenancesFor(long itemId) {
        List<ItemMaintenance> result = new ArrayList<>();
        for (ItemMaintenance maintenance : mDb.itemMaintenanceDao().findAllItemMaintenances()) {
            if (maintenance.itemId != null && maintenance.itemId == itemId) {
                result.add(maintenance);
            }
        }
        return result;
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
            providerRegistry.register(ItemMaintenanceDao.class, () -> mDb.itemMaintenanceDao());
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
