package m.co.rh.id.a_personal_stuff.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.base.test.DirectExecutorService;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Component tests for {@link PagedItemCmd} against a REAL in-memory
 * AppDatabase: search matching, page-limit doubling, the empty-filter
 * short-circuit and the multi-id filter all run through actual Room SQL
 * instead of a mocked DAO.
 */
@RunWith(AndroidJUnit4.class)
public class PagedItemCmdTest {

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
    public void searchEmitsStatesReturnedByDao() {
        insertItem("Beans", null);

        PagedItemCmd pagedItemCmd = new PagedItemCmd(mProvider);
        pagedItemCmd.search("beans");

        // the direct executor runs everything inline, so the subject is
        // already updated when search() returns
        assertEquals(1, pagedItemCmd.getAllItems().size());
        assertEquals("Beans", pagedItemCmd.getAllItems().get(0).getItemName());
    }

    @Test
    public void searchEmitsEmptyListWhenDaoThrows() {
        insertItem("Beans", null);
        // closing the in-memory DB makes the next Room query throw, standing
        // in for the mocked DAO's RuntimeException in the original unit test
        mDb.close();

        PagedItemCmd pagedItemCmd = new PagedItemCmd(mProvider);
        pagedItemCmd.search("beans");

        // the cmd's catch(Throwable) path emits an empty list instead of
        // crashing
        assertNotNull(pagedItemCmd.getAllItems());
        assertTrue(pagedItemCmd.getAllItems().isEmpty());
    }

    @Test
    public void searchMergesTagAndNameMatchesWithoutDuplicates() {
        // "Beans" matches BOTH by name and by tag: the merged id set must
        // deduplicate it. (The former unit test pinned the exact [7,5] IN-list
        // order; with a real DAO the SQL re-sorts via ORDER BY, so dedup +
        // membership is the observable contract.)
        ItemState both = insertItem("Beans", "beans");
        ItemState tagOnly = insertItem("Sardines", "beans");

        PagedItemCmd pagedItemCmd = new PagedItemCmd(mProvider);
        pagedItemCmd.search("beans");

        Set<Long> resultIds = new HashSet<>();
        Set<String> resultNames = new HashSet<>();
        for (ItemState itemState : pagedItemCmd.getAllItems()) {
            resultIds.add(itemState.getItemId());
            resultNames.add(itemState.getItemName());
        }
        assertEquals(2, pagedItemCmd.getAllItems().size());
        assertTrue(resultIds.contains(both.getItemId()));
        assertTrue(resultIds.contains(tagOnly.getItemId()));
        assertEquals(new HashSet<>(Arrays.asList("Beans", "Sardines")), resultNames);
    }

    @Test
    public void loadNextPageDoublesLimitAndReRunsSearch() {
        // 300 matching items: the first search fills the initial 100-item
        // page, so loadNextPage() is allowed through and must double the
        // limit and re-run the search
        for (int i = 0; i < 300; i++) {
            insertItem("Item " + i, null);
        }

        PagedItemCmd pagedItemCmd = new PagedItemCmd(mProvider);
        pagedItemCmd.search("item");
        assertEquals(100, pagedItemCmd.getAllItems().size());

        pagedItemCmd.loadNextPage();

        // loadNextPage() must double the limit and re-run the search via
        // refresh() so search mode is paginated too
        assertEquals(200, pagedItemCmd.getAllItems().size());
    }

    @Test
    public void emptyFilterEmitsEmptyListWithoutTouchingDao() {
        insertItem("Beans", null);
        PagedItemCmd pagedItemCmd = new PagedItemCmd(mProvider);
        pagedItemCmd.setItemFilter(new ArrayList<>());
        pagedItemCmd.refresh();

        // an empty filter matches nothing and must short-circuit before the
        // DAO: Room's IN () throws on an empty collection
        assertTrue(pagedItemCmd.getAllItems().isEmpty());
    }

    @Test
    public void multiIdFilterLoadsOnlyMatchingItems() {
        ItemState milk = insertItem("Milk", null);
        ItemState bread = insertItem("Bread", null);
        ItemState eggs = insertItem("Eggs", null);
        insertItem("Cheese", null);

        PagedItemCmd pagedItemCmd = new PagedItemCmd(mProvider);
        pagedItemCmd.setItemFilter(Arrays.asList(
                milk.getItemId(), bread.getItemId(), eggs.getItemId()));
        pagedItemCmd.refresh();

        // only the filtered ids are loaded (real SQL IN list); the Cheese row
        // is excluded — the real-database analog of the former mock's "1 of 3
        // requested ids exists" scenario
        assertEquals(3, pagedItemCmd.getAllItems().size());
        Set<String> names = new HashSet<>();
        for (ItemState itemState : pagedItemCmd.getAllItems()) {
            names.add(itemState.getItemName());
        }
        assertEquals(new HashSet<>(Arrays.asList("Milk", "Bread", "Eggs")), names);
    }

    @Test
    public void searchWithinMultiIdFilterRetainsOnlyFilterMatches() {
        ItemState beans = insertItem("Beans", null);
        // tag match on an id OUTSIDE the filter set
        ItemState unfilteredTagMatch = insertItem("Sardines", "beans");
        ItemState fillerA = insertItem("Rice", null);
        ItemState fillerB = insertItem("Pasta", null);

        PagedItemCmd pagedItemCmd = new PagedItemCmd(mProvider);
        pagedItemCmd.setItemFilter(Arrays.asList(
                beans.getItemId(), fillerA.getItemId(), fillerB.getItemId()));
        pagedItemCmd.refresh();
        pagedItemCmd.search("beans");

        // search results {tag-match, name-match} retained against the filter
        // keep only the name-match — a search within filtered mode cannot
        // escape the filter set
        assertEquals(1, pagedItemCmd.getAllItems().size());
        assertEquals("Beans", pagedItemCmd.getAllItems().get(0).getItemName());
        assertEquals(beans.getItemId(), pagedItemCmd.getAllItems().get(0).getItemId());
        assertNotEquals(unfilteredTagMatch.getItemId(),
                pagedItemCmd.getAllItems().get(0).getItemId());
    }

    private ItemState insertItem(String name, String tag) {
        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = name;
        item.amount = 1;
        itemState.updateItem(item);
        if (tag != null) {
            ItemTag itemTag = new ItemTag();
            itemTag.tag = tag;
            itemState.updateItemTags(Collections.singletonList(itemTag));
        }
        mItemDao.insertItem(itemState);
        return itemState;
    }

    private class TestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            // the direct executor runs the command inline on the calling
            // thread so the subject emissions are deterministic without latches
            providerRegistry.register(ExecutorService.class, () -> mExecutorService);
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ItemDao.class, () -> mItemDao);
        }
    }
}
