package m.co.rh.id.a_personal_stuff.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.base.test.DirectExecutorService;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Component tests for {@link ArchiveItemCmd} against a REAL in-memory
 * AppDatabase: the archived flag persistence, the active-only query
 * exclusion and the updated_date_time bump all run through actual Room SQL,
 * while the emissions are observed on a REAL {@link ItemChangeNotifier}
 * shared with the command. The two failed-write rollback tests instead use
 * {@link FailingItemDao}, a stub whose updateItem always throws: forcing a
 * real Room write failure is impractical here because closing the in-memory
 * DB does NOT work — Room's SupportSQLiteOpenHelper lazily re-opens a closed
 * database on the next write, so the update silently succeeds against a
 * fresh, empty DB and the failure is masked. The hard safety property pinned
 * here: archive/unarchive must NEVER emit on
 * {@link ItemChangeNotifier#getDeletedItemFlow()}.
 */
@RunWith(AndroidJUnit4.class)
public class ArchiveItemCmdTest {

    private static final String DELETED_FLOW_MESSAGE =
            "ArchiveItemCmd must NEVER emit on getDeletedItemFlow(): the module"
                    + " event handlers wipe the item's child data on that flow";

    private Context mContext;
    private AppDatabase mDb;
    private ExecutorService mExecutorService;
    private Provider mProvider;
    private ItemDao mItemDao;
    private FailingItemDao mFailingItemDao;
    private ItemChangeNotifier mItemChangeNotifier;
    private CompositeDisposable mCompositeDisposable;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(mContext, AppDatabase.class)
                .allowMainThreadQueries().build();
        mItemDao = mDb.itemDao();
        mFailingItemDao = new FailingItemDao();
        mExecutorService = new DirectExecutorService();
        // ONE notifier instance per test, shared by the command and the
        // test's subscriptions
        mItemChangeNotifier = new ItemChangeNotifier();
        mCompositeDisposable = new CompositeDisposable();
        mProvider = Provider.createProvider(mContext, new TestProviderModule());
    }

    @After
    public void tearDown() {
        if (mCompositeDisposable != null) {
            mCompositeDisposable.dispose();
        }
        if (mDb != null) {
            mDb.close();
        }
    }

    @Test
    public void archivePersistsFlagAndEmitsOnlyArchivedFlow() {
        ItemState itemState = insertItem("Beans");
        // subscribe BEFORE executing: the flows are backed by PublishSubjects,
        // so a late subscriber would miss the emission
        List<ItemState> archivedEvents = new ArrayList<>();
        List<ItemState> unarchivedEvents = new ArrayList<>();
        List<ItemState> deletedEvents = new ArrayList<>();
        List<ItemState> anyChangeEvents = new ArrayList<>();
        CompositeDisposable disposables = new CompositeDisposable();
        mCompositeDisposable.add(disposables);
        try {
            disposables.add(mItemChangeNotifier.getArchivedItemFlow()
                    .subscribe(archivedEvents::add));
            disposables.add(mItemChangeNotifier.getUnarchivedItemFlow()
                    .subscribe(unarchivedEvents::add));
            disposables.add(mItemChangeNotifier.getDeletedItemFlow()
                    .subscribe(deletedEvents::add));
            disposables.add(mItemChangeNotifier.getAnyItemChangeFlow()
                    .subscribe(anyChangeEvents::add));

            ArchiveItemCmd archiveItemCmd = new ArchiveItemCmd(mProvider);
            // the direct executor runs the command inline, so the emissions
            // have already happened when blockingGet() returns
            ItemState result = archiveItemCmd.archive(itemState).blockingGet();

            assertTrue(result.isArchived());
            // the flag is persisted, not just flipped in memory
            Item persistedItem = mItemDao.findItemById(itemState.getItemId());
            assertNotNull(persistedItem);
            assertTrue(persistedItem.archived);
            // the active-only query excludes the archived row
            assertFalse(containsItem(mItemDao.findItemStateWithLimit(10, null),
                    itemState.getItemId()));

            assertEquals(1, archivedEvents.size());
            assertEquals(itemState.getItemId(), archivedEvents.get(0).getItemId());
            assertEquals("Beans", archivedEvents.get(0).getItemName());
            assertTrue(unarchivedEvents.isEmpty());
            // the critical safety constraint
            assertTrue(DELETED_FLOW_MESSAGE, deletedEvents.isEmpty());
            // exactly one change overall: the archived emission, merged
            assertEquals(1, anyChangeEvents.size());
        } finally {
            disposables.dispose();
        }
    }

    @Test
    public void unarchiveRestoresFlagAndEmitsUnarchivedFlow() {
        ItemState itemState = insertItem("Milk");
        List<ItemState> archivedEvents = new ArrayList<>();
        List<ItemState> unarchivedEvents = new ArrayList<>();
        List<ItemState> deletedEvents = new ArrayList<>();
        CompositeDisposable disposables = new CompositeDisposable();
        mCompositeDisposable.add(disposables);
        try {
            disposables.add(mItemChangeNotifier.getArchivedItemFlow()
                    .subscribe(archivedEvents::add));
            disposables.add(mItemChangeNotifier.getUnarchivedItemFlow()
                    .subscribe(unarchivedEvents::add));
            disposables.add(mItemChangeNotifier.getDeletedItemFlow()
                    .subscribe(deletedEvents::add));

            ArchiveItemCmd archiveItemCmd = new ArchiveItemCmd(mProvider);
            archiveItemCmd.archive(itemState).blockingGet();
            ItemState result = archiveItemCmd.unarchive(itemState).blockingGet();

            // the flag is restored in memory AND in the database
            assertFalse(result.isArchived());
            assertFalse(itemState.isArchived());
            Item persistedItem = mItemDao.findItemById(itemState.getItemId());
            assertNotNull(persistedItem);
            assertFalse(persistedItem.archived);
            // the item reappears in the active-only query
            assertTrue(containsItem(mItemDao.findItemStateWithLimit(10, null),
                    itemState.getItemId()));

            assertEquals(1, unarchivedEvents.size());
            assertEquals(itemState.getItemId(), unarchivedEvents.get(0).getItemId());
            // the single archived emission comes from the archive step
            assertEquals(1, archivedEvents.size());
            assertTrue(DELETED_FLOW_MESSAGE, deletedEvents.isEmpty());
        } finally {
            disposables.dispose();
        }
    }

    @Test
    public void archiveBumpsUpdatedDateTime() {
        // epoch timestamps set BEFORE the insert keep the comparison
        // deterministic: "now" is always strictly after them, with no
        // same-millisecond race against the insert
        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Rice";
        item.amount = 1;
        item.createdDateTime = new Date(0L);
        item.updatedDateTime = new Date(0L);
        itemState.updateItem(item);
        mItemDao.insertItem(itemState);
        Date updatedDateTimeAfterInsert =
                mItemDao.findItemById(itemState.getItemId()).updatedDateTime;
        assertEquals(new Date(0L), updatedDateTimeAfterInsert);

        ArchiveItemCmd archiveItemCmd = new ArchiveItemCmd(mProvider);
        archiveItemCmd.archive(itemState).blockingGet();

        // updateItem bumps updated_date_time — the archived list sorts on it
        Item persistedItem = mItemDao.findItemById(itemState.getItemId());
        assertNotNull(persistedItem.updatedDateTime);
        assertTrue(persistedItem.updatedDateTime.after(updatedDateTimeAfterInsert));
        // the bump comes from the update, the creation timestamp is untouched
        assertEquals(new Date(0L), persistedItem.createdDateTime);
    }

    @Test
    public void archiveAndUnarchiveEmitClonesNotTheCallerInstance() {
        ItemState itemState = insertItem("Eggs");
        List<ItemState> archivedEvents = new ArrayList<>();
        List<ItemState> unarchivedEvents = new ArrayList<>();
        CompositeDisposable disposables = new CompositeDisposable();
        mCompositeDisposable.add(disposables);
        try {
            disposables.add(mItemChangeNotifier.getArchivedItemFlow()
                    .subscribe(archivedEvents::add));
            disposables.add(mItemChangeNotifier.getUnarchivedItemFlow()
                    .subscribe(unarchivedEvents::add));

            ArchiveItemCmd archiveItemCmd = new ArchiveItemCmd(mProvider);
            archiveItemCmd.archive(itemState).blockingGet();
            archiveItemCmd.unarchive(itemState).blockingGet();

            assertEquals(1, archivedEvents.size());
            assertEquals(1, unarchivedEvents.size());
            ItemState archivedEmission = archivedEvents.get(0);
            ItemState unarchivedEmission = unarchivedEvents.get(0);

            // subscribers get their own copy, never the instance the caller
            // (the adapter's row) keeps mutating
            assertNotSame(itemState, archivedEmission);
            assertNotSame(itemState, unarchivedEmission);
            assertNotSame(itemState.getItem(), archivedEmission.getItem());
            assertNotSame(itemState.getItem(), unarchivedEmission.getItem());
            assertEquals(itemState.getItemId(), archivedEmission.getItemId());
            assertEquals(itemState.getItemId(), unarchivedEmission.getItemId());
            // the clones snapshot the flag at emit time: the later unarchive
            // on the caller's instance must not rewrite the archived emission
            assertTrue(archivedEmission.isArchived());
            assertFalse(unarchivedEmission.isArchived());
        } finally {
            disposables.dispose();
        }
    }

    @Test
    public void unarchiveRollsBackFlagWhenDbWriteFails() {
        ItemState itemState = insertItem("Bread");
        // archive through the REAL cmd first: the write succeeds and the
        // archived flag is persisted
        ArchiveItemCmd realArchiveItemCmd = new ArchiveItemCmd(mProvider);
        realArchiveItemCmd.archive(itemState).blockingGet();
        assertTrue(itemState.isArchived());
        assertTrue(mItemDao.findItemById(itemState.getItemId()).archived);
        List<ItemState> archivedEvents = new ArrayList<>();
        List<ItemState> unarchivedEvents = new ArrayList<>();
        List<ItemState> deletedEvents = new ArrayList<>();
        CompositeDisposable disposables = new CompositeDisposable();
        mCompositeDisposable.add(disposables);
        try {
            disposables.add(mItemChangeNotifier.getArchivedItemFlow()
                    .subscribe(archivedEvents::add));
            disposables.add(mItemChangeNotifier.getUnarchivedItemFlow()
                    .subscribe(unarchivedEvents::add));
            disposables.add(mItemChangeNotifier.getDeletedItemFlow()
                    .subscribe(deletedEvents::add));

            // the failing-dao cmd turns the next updateItem into a forced
            // write failure (see FailingItemDao) — the DB itself stays open
            ArchiveItemCmd failingArchiveItemCmd = failingDaoArchiveItemCmd();
            Throwable thrown = null;
            try {
                failingArchiveItemCmd.unarchive(itemState).blockingGet();
            } catch (Throwable throwable) {
                thrown = throwable;
            }

            assertNotNull("unarchive must error when the DB write fails", thrown);
            assertEquals("Forced DB write failure", thrown.getMessage());
            // the rollback restored the previous value instead of leaving
            // the in-memory flag flipped while the DB still says archived
            assertTrue(itemState.isArchived());
            // a failed write publishes nothing: the single archived emission
            // happened BEFORE these subscriptions (the real archive above —
            // PublishSubjects do not replay), and the failed unarchive adds
            // no new emission on any flow
            assertTrue(archivedEvents.isEmpty());
            assertTrue(unarchivedEvents.isEmpty());
            assertTrue(DELETED_FLOW_MESSAGE, deletedEvents.isEmpty());
        } finally {
            disposables.dispose();
        }
    }

    @Test
    public void archiveRollsBackFlagWhenDbWriteFails() {
        // the row is inserted via the REAL dao: the DB stays open, only the
        // cmd's write path is broken (see FailingItemDao — closing the DB is
        // NOT a viable trigger because Room lazily re-opens a closed
        // in-memory database on the next write)
        ItemState itemState = insertItem("Cheese");
        assertFalse(itemState.isArchived());
        List<ItemState> archivedEvents = new ArrayList<>();
        List<ItemState> unarchivedEvents = new ArrayList<>();
        List<ItemState> deletedEvents = new ArrayList<>();
        CompositeDisposable disposables = new CompositeDisposable();
        mCompositeDisposable.add(disposables);
        try {
            disposables.add(mItemChangeNotifier.getArchivedItemFlow()
                    .subscribe(archivedEvents::add));
            disposables.add(mItemChangeNotifier.getUnarchivedItemFlow()
                    .subscribe(unarchivedEvents::add));
            disposables.add(mItemChangeNotifier.getDeletedItemFlow()
                    .subscribe(deletedEvents::add));

            ArchiveItemCmd archiveItemCmd = failingDaoArchiveItemCmd();
            Throwable thrown = null;
            try {
                archiveItemCmd.archive(itemState).blockingGet();
            } catch (Throwable throwable) {
                thrown = throwable;
            }

            assertNotNull("archive must error when the DB write fails", thrown);
            assertEquals("Forced DB write failure", thrown.getMessage());
            // the rollback restored the previous value instead of leaving
            // the in-memory flag flipped while the DB still says active
            assertFalse(itemState.isArchived());
            // a failed write publishes nothing
            assertTrue(archivedEvents.isEmpty());
            assertTrue(unarchivedEvents.isEmpty());
            assertTrue(DELETED_FLOW_MESSAGE, deletedEvents.isEmpty());
        } finally {
            disposables.dispose();
        }
    }

    private ItemState insertItem(String name) {
        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = name;
        item.amount = 1;
        itemState.updateItem(item);
        mItemDao.insertItem(itemState);
        return itemState;
    }

    private boolean containsItem(List<ItemState> itemStates, Long itemId) {
        for (ItemState itemState : itemStates) {
            if (itemId.equals(itemState.getItemId())) {
                return true;
            }
        }
        return false;
    }

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            // the direct executor runs the command inline on the calling
            // thread so the notifier emissions are deterministic without
            // latches
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemDao.class, () -> mItemDao);
            // the SAME instance the tests subscribe to
            providerRegistry.register(ItemChangeNotifier.class, () -> mItemChangeNotifier);
        }
    }

    /**
     * Builds an {@link ArchiveItemCmd} wired to {@link #mFailingItemDao}:
     * identical wiring to the real provider except ItemDao resolves to the
     * failure-injecting stub. The REAL in-memory DB stays open throughout.
     */
    private ArchiveItemCmd failingDaoArchiveItemCmd() {
        return new ArchiveItemCmd(
                Provider.createProvider(mContext, new FailingDaoTestProviderModule()));
    }

    /**
     * Provider module for {@link #failingDaoArchiveItemCmd()}: the same
     * registrations as {@link TestProviderModule} but ItemDao resolves to
     * {@link #mFailingItemDao}.
     */
    private class FailingDaoTestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemDao.class, () -> mFailingItemDao);
            // the SAME instance the tests subscribe to
            providerRegistry.register(ItemChangeNotifier.class, () -> mItemChangeNotifier);
        }
    }

    /**
     * DAO whose updateItem always throws — forces the cmd's rollback path
     * without relying on DB-close semantics (Room lazily re-opens a closed
     * in-memory DB on the next write, which silently succeeds and masks the
     * failure). Every remaining abstract method of {@link ItemDao} throws
     * UnsupportedOperationException: the rollback tests hit the database
     * only through the REAL dao, and the cmd itself only calls
     * {@link ItemDao#updateItem(ItemState)}. Room annotations are not
     * repeated here because this subclass is a plain Java stub never seen by
     * Room's annotation processor.
     */
    private class FailingItemDao extends ItemDao {
        private static final String NOT_USED_MESSAGE = "not used by the rollback tests";

        @Override
        public void updateItem(ItemState itemState) {
            throw new IllegalStateException("Forced DB write failure");
        }

        @Override
        protected void deleteItemImagesByItemId(long itemId) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        protected void deleteItemTagsByItemId(long itemId) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        protected long insert(Item item) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        protected void update(Item item) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        protected void delete(Item item) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        protected List<ItemTag> findItemTagsByItemId(Long itemId) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public List<ItemImage> findItemImagesByItemIds(List<Long> itemIds) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public List<ItemTag> findItemTagsByItemIds(List<Long> itemIds) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public List<Item> searchItem(String search) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public List<Item> searchArchivedItem(String search) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public List<Item> searchItemBarcode(String search) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public List<ItemTag> searchItemTag(String search) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        protected List<Item> findItemsRaw(SupportSQLiteQuery query) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        protected long insert(ItemImage itemImage) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        protected void delete(ItemImage itemImage) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public List<ItemImage> findItemImagesByItemId(long itemId) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public ItemImage findItemImageByFileName(String fileName) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public long insert(ItemTag itemTag) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public void delete(ItemTag itemTag) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public Item findItemById(long itemId) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public List<Item> findAllItems() {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public List<Long> getAllItemIds() {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public int countItems() {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public int countAllItems() {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public Double sumInventoryValue() {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public Double sumInventoryValueIncludingArchived() {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public List<Item> findItemsExpiringBefore(Date to) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public int countItemsExpiringBefore(Date to) {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }

        @Override
        public List<Item> findItemsWithMinAmount() {
            throw new UnsupportedOperationException(NOT_USED_MESSAGE);
        }
    }
}
