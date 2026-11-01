package m.co.rh.id.a_personal_stuff.item_usage.provider.component;

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

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.base.test.AndroidTestUtils;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.a_personal_stuff.item_usage.room.ItemUsageDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

@RunWith(AndroidJUnit4.class)
public class ItemUsageEventHandlerTest {

    private Context mContext;
    private ItemUsageDatabase mDb;
    private ExecutorService mExecutorService;
    private ItemChangeNotifier mNotifier;
    private Provider mProvider;
    private ItemUsageEventHandler mHandler;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(mContext, ItemUsageDatabase.class)
                .allowMainThreadQueries().build();
        mExecutorService = Executors.newSingleThreadExecutor();
        mNotifier = new ItemChangeNotifier();
        mProvider = Provider.createProvider(mContext, new TestProviderModule());
        mHandler = new ItemUsageEventHandler(mProvider);
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
    public void deletedItemEventDeletesUsageAndImageRows() throws Exception {
        long deletedItemId = 1L;
        long survivingItemId = 2L;
        Date now = new Date();

        ItemUsage deletedUsage = new ItemUsage();
        deletedUsage.itemId = deletedItemId;
        deletedUsage.description = "deleted usage";
        deletedUsage.amount = 1;
        deletedUsage.createdDateTime = now;
        long deletedUsageId = mDb.itemUsageDao().insert(deletedUsage);

        ItemUsageImage deletedImage = new ItemUsageImage();
        deletedImage.itemUsageId = deletedUsageId;
        deletedImage.fileName = "deleted_usage.jpg";
        deletedImage.createdDateTime = now;
        mDb.itemUsageDao().insert(deletedImage);

        ItemUsage survivingUsage = new ItemUsage();
        survivingUsage.itemId = survivingItemId;
        survivingUsage.description = "surviving usage";
        survivingUsage.amount = 1;
        survivingUsage.createdDateTime = now;
        long survivingUsageId = mDb.itemUsageDao().insert(survivingUsage);

        ItemUsageImage survivingImage = new ItemUsageImage();
        survivingImage.itemUsageId = survivingUsageId;
        survivingImage.fileName = "surviving_usage.jpg";
        survivingImage.createdDateTime = now;
        mDb.itemUsageDao().insert(survivingImage);

        mNotifier.itemDeleted(deletedItemState(deletedItemId));

        // the handler processes the event on its executor thread, so poll
        assertTrue(AndroidTestUtils.await(() -> mDb.itemUsageDao().findItemUsageByItemId(deletedItemId).isEmpty()));
        // the second-level image row must be gone with its parent
        assertNull(mDb.itemUsageDao().findItemUsageImageByFileName("deleted_usage.jpg"));
        // other items' rows survive untouched
        assertEquals(1, mDb.itemUsageDao().findItemUsageByItemId(survivingItemId).size());
        assertNotNull(mDb.itemUsageDao().findItemUsageImageByFileName("surviving_usage.jpg"));
    }

    private ItemState deletedItemState(long itemId) {
        Item item = new Item();
        item.id = itemId;
        item.name = "deleted item " + itemId;
        ItemState itemState = new ItemState();
        itemState.updateItem(item);
        return itemState;
    }

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemChangeNotifier.class, () -> mNotifier);
            providerRegistry.register(ItemUsageDao.class, () -> mDb.itemUsageDao());
        }
    }
}
