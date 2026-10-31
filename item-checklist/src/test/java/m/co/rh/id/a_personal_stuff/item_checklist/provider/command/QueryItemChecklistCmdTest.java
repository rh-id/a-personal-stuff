package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.aprovider.Provider;

/**
 * Unit tests for {@link QueryItemChecklistCmd#existsById} — the DB-backed
 * existence check that keeps the checklist-list page's designated
 * shopping-list badge off dangling ids (deleted checklists) without touching
 * {@code findItemChecklistStateById}, whose DAO state builder NPEs on a
 * stale id.
 */
public class QueryItemChecklistCmdTest {

    private ItemChecklistDao mItemChecklistDao;
    private QueryItemChecklistCmd mQueryItemChecklistCmd;

    @Before
    public void setUp() {
        Provider provider = mock(Provider.class);
        mItemChecklistDao = mock(ItemChecklistDao.class);
        when(provider.get(ExecutorService.class)).thenReturn(new DirectExecutorService());
        when(provider.get(ItemChecklistDao.class)).thenReturn(mItemChecklistDao);
        mQueryItemChecklistCmd = new QueryItemChecklistCmd(provider);
    }

    @Test
    public void existsByIdTrueWhenChecklistExists() {
        when(mItemChecklistDao.countItemChecklistsById(5L)).thenReturn(1);

        Boolean exists = mQueryItemChecklistCmd.existsById(5L).blockingGet();

        assertTrue(exists);
        verify(mItemChecklistDao).countItemChecklistsById(5L);
        verifyNoMoreInteractions(mItemChecklistDao);
    }

    @Test
    public void existsByIdFalseWhenChecklistDeleted() {
        // a dangling stored id must read as missing so the badge clears
        when(mItemChecklistDao.countItemChecklistsById(7L)).thenReturn(0);

        Boolean exists = mQueryItemChecklistCmd.existsById(7L).blockingGet();

        assertFalse(exists);
        verify(mItemChecklistDao).countItemChecklistsById(7L);
        verifyNoMoreInteractions(mItemChecklistDao);
    }

    /**
     * Runs every task inline on the calling thread: keeps the Single's
     * subscribeOn path exercised without spawning threads (mirrors
     * GenerateShoppingListCmdTest's executor stub).
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
