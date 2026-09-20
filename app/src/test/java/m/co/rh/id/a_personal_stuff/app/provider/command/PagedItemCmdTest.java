package m.co.rh.id.a_personal_stuff.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class PagedItemCmdTest {

    private Provider mProvider;
    private ItemDao mItemDao;
    private ILogger mLogger;

    @Before
    public void setUp() {
        mProvider = mock(Provider.class);
        mItemDao = mock(ItemDao.class);
        mLogger = mock(ILogger.class);
        when(mProvider.get(ExecutorService.class)).thenReturn(new DirectExecutorService());
        when(mProvider.get(ItemDao.class)).thenReturn(mItemDao);
        when(mProvider.get(ILogger.class)).thenReturn(mLogger);
    }

    @Test
    public void searchEmitsStatesReturnedByDao() {
        ItemState itemState = new ItemState();
        Item item = new Item();
        item.id = 5L;
        item.name = "Beans";
        itemState.updateItem(item);
        ArrayList<ItemState> daoResult = new ArrayList<>(Collections.singletonList(itemState));
        when(mItemDao.findItemStateByIdsWithLimit(anyList(),
                nullable(ItemDao.QueryOrderBy.class), eq(100))).thenReturn(daoResult);

        PagedItemCmd pagedItemCmd = new PagedItemCmd(mProvider);
        pagedItemCmd.search("beans");

        // the direct executor runs everything inline, so the subject is
        // already updated when search() returns
        assertEquals(1, pagedItemCmd.getAllItems().size());
        assertEquals("Beans", pagedItemCmd.getAllItems().get(0).getItemName());
    }

    @Test
    public void searchEmitsEmptyListWhenDaoThrows() {
        when(mItemDao.findItemStateByIdsWithLimit(anyList(),
                nullable(ItemDao.QueryOrderBy.class), eq(100)))
                .thenThrow(new RuntimeException("db down"));

        PagedItemCmd pagedItemCmd = new PagedItemCmd(mProvider);
        pagedItemCmd.search("beans");

        assertNotNull(pagedItemCmd.getAllItems());
        assertTrue(pagedItemCmd.getAllItems().isEmpty());
        verify(mLogger).e(eq(PagedItemCmd.class.getName()),
                nullable(String.class), any(RuntimeException.class));
    }

    @Test
    public void searchPassesItemIdsAndLimitToDao() {
        ItemTag itemTag = new ItemTag();
        itemTag.itemId = 7L;
        when(mItemDao.searchItemTag("beans"))
                .thenReturn(new ArrayList<>(Collections.singletonList(itemTag)));
        Item item = new Item();
        item.id = 5L;
        when(mItemDao.searchItem("beans"))
                .thenReturn(new ArrayList<>(Collections.singletonList(item)));
        when(mItemDao.findItemStateByIdsWithLimit(anyList(),
                nullable(ItemDao.QueryOrderBy.class), eq(100))).thenReturn(new ArrayList<>());

        PagedItemCmd pagedItemCmd = new PagedItemCmd(mProvider);
        pagedItemCmd.search("beans");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> itemIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mItemDao).findItemStateByIdsWithLimit(itemIdsCaptor.capture(),
                nullable(ItemDao.QueryOrderBy.class), eq(100));
        // tag matches first, then name; the LinkedHashSet keeps the order
        assertEquals(Arrays.asList(7L, 5L), itemIdsCaptor.getValue());
    }

    @Test
    public void loadNextPageDoublesLimitAndReRunsSearch() {
        // the DAO returns exactly as many states as the limit asks for, so
        // the getAllItems().size() < mLimit guard lets loadNextPage() through
        when(mItemDao.findItemStateByIdsWithLimit(anyList(),
                nullable(ItemDao.QueryOrderBy.class), anyInt()))
                .thenAnswer(invocation -> {
                    int limit = invocation.getArgument(2);
                    ArrayList<ItemState> result = new ArrayList<>();
                    for (int i = 0; i < limit; i++) {
                        ItemState itemState = new ItemState();
                        Item item = new Item();
                        item.id = (long) i;
                        item.name = "Item " + i;
                        itemState.updateItem(item);
                        result.add(itemState);
                    }
                    return result;
                });

        PagedItemCmd pagedItemCmd = new PagedItemCmd(mProvider);
        pagedItemCmd.search("beans");
        assertEquals(100, pagedItemCmd.getAllItems().size());
        verify(mItemDao).findItemStateByIdsWithLimit(anyList(),
                nullable(ItemDao.QueryOrderBy.class), eq(100));

        pagedItemCmd.loadNextPage();

        // loadNextPage() must double the limit and re-run the search via
        // refresh() so search mode is paginated too
        verify(mItemDao).findItemStateByIdsWithLimit(anyList(),
                nullable(ItemDao.QueryOrderBy.class), eq(200));
        assertEquals(200, pagedItemCmd.getAllItems().size());
    }

    /**
     * Runs every command on the calling thread so the Flowable/subject
     * emissions are deterministic without latches.
     */
    private static class DirectExecutorService extends AbstractExecutorService {
        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            // no-op
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
