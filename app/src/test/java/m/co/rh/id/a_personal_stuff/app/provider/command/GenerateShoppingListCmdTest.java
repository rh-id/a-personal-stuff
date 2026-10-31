package m.co.rh.id.a_personal_stuff.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.component.InventoryStatsCalculator;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.component.ItemChecklistService;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.aprovider.Provider;

public class GenerateShoppingListCmdTest {

    private Provider mProvider;
    private Context mContext;
    private ItemChecklistDao mItemChecklistDao;
    private SettingsSharedPreferences mSettingsSharedPreferences;
    private InventoryStatsCalculator mInventoryStatsCalculator;
    private ItemChecklistService mItemChecklistService;

    @Before
    public void setUp() {
        mProvider = mock(Provider.class);
        mContext = mock(Context.class);
        mItemChecklistDao = mock(ItemChecklistDao.class);
        mSettingsSharedPreferences = mock(SettingsSharedPreferences.class);
        mInventoryStatsCalculator = mock(InventoryStatsCalculator.class);
        mItemChecklistService = mock(ItemChecklistService.class);
        when(mProvider.get(ExecutorService.class)).thenReturn(new DirectExecutorService());
        when(mProvider.get(ItemChecklistDao.class)).thenReturn(mItemChecklistDao);
        when(mProvider.get(SettingsSharedPreferences.class)).thenReturn(mSettingsSharedPreferences);
        when(mProvider.get(InventoryStatsCalculator.class)).thenReturn(mInventoryStatsCalculator);
        when(mProvider.get(ItemChecklistService.class)).thenReturn(mItemChecklistService);
        when(mProvider.getContext()).thenReturn(mContext);
        when(mContext.getApplicationContext()).thenReturn(mContext);
        when(mContext.getString(R.string.shopping_list_title)).thenReturn("Shopping list");
    }

    @Test
    public void createsChecklistAndPersistsIdWhenNothingStored() {
        when(mSettingsSharedPreferences.getShoppingListChecklistId()).thenReturn(0L);
        when(mItemChecklistDao.findItemChecklistByTitle("Shopping list")).thenReturn(null);
        when(mItemChecklistService.createChecklist(any(ItemChecklistState.class)))
                .thenAnswer(invocation -> {
                    ItemChecklistState state = invocation.getArgument(0);
                    state.getItemChecklist().id = 10L;
                    return state;
                });
        when(mInventoryStatsCalculator.findAlertItemIds(anySet(), any(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(1L)));
        when(mItemChecklistService.addItemIds(eq(10L), anyList())).thenReturn(2);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(10L, result.checklistId);
        assertEquals(2, result.addedCount);
        assertEquals(1, result.lowStockCount);
        // the new designated id must be persisted, otherwise every tap would
        // fall through to the title lookup again
        verify(mSettingsSharedPreferences).setShoppingListChecklistId(10L);
    }

    @Test
    public void usesStoredChecklistWhenIdStillExists() {
        when(mSettingsSharedPreferences.getShoppingListChecklistId()).thenReturn(5L);
        when(mItemChecklistDao.countItemChecklistsById(5L)).thenReturn(1);
        when(mInventoryStatsCalculator.findAlertItemIds(anySet(), any(), anyInt()))
                .thenReturn(new ArrayList<>(Arrays.asList(1L, 2L)));
        when(mItemChecklistService.addItemIds(eq(5L), anyList())).thenReturn(2);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(5L, result.checklistId);
        assertEquals(2, result.lowStockCount);
        verify(mItemChecklistService, never()).createChecklist(any());
        verify(mItemChecklistDao, never()).findItemChecklistByTitle(anyString());
        verify(mSettingsSharedPreferences, never()).setShoppingListChecklistId(anyLong());
    }

    @Test
    public void adoptsExistingListByTitleWhenStoredIdIsStale() {
        when(mSettingsSharedPreferences.getShoppingListChecklistId()).thenReturn(7L);
        when(mItemChecklistDao.countItemChecklistsById(7L)).thenReturn(0);
        ItemChecklist adopted = new ItemChecklist();
        adopted.id = 3L;
        adopted.title = "Shopping list";
        when(mItemChecklistDao.findItemChecklistByTitle("Shopping list")).thenReturn(adopted);
        when(mInventoryStatsCalculator.findAlertItemIds(anySet(), any(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(1L)));
        when(mItemChecklistService.addItemIds(eq(3L), anyList())).thenReturn(1);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(3L, result.checklistId);
        // adoption is a one-time repair: the recovered id is re-persisted
        verify(mSettingsSharedPreferences).setShoppingListChecklistId(3L);
        verify(mItemChecklistService, never()).createChecklist(any());
    }

    @Test
    public void reTapWithNothingNewReportsZeroAdded() {
        when(mSettingsSharedPreferences.getShoppingListChecklistId()).thenReturn(5L);
        when(mItemChecklistDao.countItemChecklistsById(5L)).thenReturn(1);
        when(mInventoryStatsCalculator.findAlertItemIds(anySet(), any(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(1L)));
        // every low-stock item is already on the list (checked or not)
        when(mItemChecklistService.addItemIds(eq(5L), anyList())).thenReturn(0);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(5L, result.checklistId);
        assertEquals(0, result.addedCount);
        assertEquals(1, result.lowStockCount);
    }

    @Test
    public void emptyLowStockSkipsFindOrCreateAndAdd() {
        when(mInventoryStatsCalculator.findAlertItemIds(anySet(), any(), anyInt()))
                .thenReturn(new ArrayList<>());

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(0, result.lowStockCount);
        assertEquals(0, result.addedCount);
        assertEquals(0L, result.checklistId);
        // nothing to place: the designated checklist must not be touched,
        // let alone created
        verify(mItemChecklistDao, never()).countItemChecklistsById(anyLong());
        verify(mItemChecklistDao, never()).findItemChecklistByTitle(anyString());
        verify(mItemChecklistService, never()).createChecklist(any());
        verify(mItemChecklistService, never()).uncheckItemIds(anyLong(), anyList());
        verify(mItemChecklistService, never()).addItemIds(anyLong(), anyList());
        verify(mSettingsSharedPreferences, never()).setShoppingListChecklistId(anyLong());
    }

    @Test
    public void concurrentExecutesCreateChecklistOnlyOnce() throws Exception {
        // simulate persistence so the second thread sees the id the first
        // thread stored, and force both threads to overlap before the
        // find-or-create lock
        CountDownLatch overlapLatch = new CountDownLatch(2);
        AtomicLong storedId = new AtomicLong(0L);
        when(mSettingsSharedPreferences.getShoppingListChecklistId())
                .thenAnswer(invocation -> storedId.get());
        doAnswer(invocation -> {
            storedId.set(invocation.getArgument(0));
            return null;
        }).when(mSettingsSharedPreferences).setShoppingListChecklistId(anyLong());
        when(mItemChecklistDao.countItemChecklistsById(anyLong())).thenReturn(1);
        when(mItemChecklistDao.findItemChecklistByTitle(anyString())).thenReturn(null);
        when(mItemChecklistService.createChecklist(any(ItemChecklistState.class)))
                .thenAnswer(invocation -> {
                    ItemChecklistState state = invocation.getArgument(0);
                    state.getItemChecklist().id = 10L;
                    return state;
                });
        when(mInventoryStatsCalculator.findAlertItemIds(anySet(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    overlapLatch.countDown();
                    // hold both threads in the pre-lock section so they race
                    // into find-or-create together; bounded so a non-overlapping
                    // run cannot hang the test
                    overlapLatch.await(5, TimeUnit.SECONDS);
                    return new ArrayList<>(Collections.singletonList(1L));
                });
        when(mItemChecklistService.addItemIds(anyLong(), anyList())).thenReturn(1);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();
        Thread threadA = new Thread(() -> {
            try {
                cmd.execute().blockingGet();
            } catch (Throwable throwable) {
                errorA.set(throwable);
            }
        });
        Thread threadB = new Thread(() -> {
            try {
                cmd.execute().blockingGet();
            } catch (Throwable throwable) {
                errorB.set(throwable);
            }
        });
        threadA.start();
        threadB.start();
        threadA.join(10000);
        threadB.join(10000);

        assertNull(errorA.get());
        assertNull(errorB.get());
        // the shared pool never cancels a disposed callable, so without the
        // find-or-create lock both threads would pass the "no designated list"
        // check and create two same-title checklists
        verify(mItemChecklistService, times(1)).createChecklist(any());
    }

    @Test
    public void regenerationUnchecksCheckedRowsBeforeAdding() {
        when(mSettingsSharedPreferences.getShoppingListChecklistId()).thenReturn(5L);
        when(mItemChecklistDao.countItemChecklistsById(5L)).thenReturn(1);
        when(mInventoryStatsCalculator.findAlertItemIds(anySet(), any(), anyInt()))
                .thenReturn(new ArrayList<>(Arrays.asList(1L, 2L)));
        // a fully-checked stale list must re-open: regeneration un-checks the
        // low-stock rows before the fresh rows are appended (the bump/notify
        // side effects of that un-check are asserted in
        // ItemChecklistServiceTest)
        when(mItemChecklistService.uncheckItemIds(eq(5L), anyList())).thenReturn(1);
        when(mItemChecklistService.addItemIds(eq(5L), anyList())).thenReturn(1);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(5L, result.checklistId);
        assertEquals(1, result.addedCount);
        assertEquals(2, result.lowStockCount);
        verify(mItemChecklistService).uncheckItemIds(eq(5L), anyList());
        // regeneration strictly precedes the append, otherwise already-checked
        // rows would be inserted twice as unchecked duplicates
        InOrder inOrderService = inOrder(mItemChecklistService);
        inOrderService.verify(mItemChecklistService).uncheckItemIds(eq(5L), anyList());
        inOrderService.verify(mItemChecklistService).addItemIds(eq(5L), anyList());
    }

    @Test
    public void regenerationWithNothingToUncheckStillAddsItems() {
        when(mSettingsSharedPreferences.getShoppingListChecklistId()).thenReturn(5L);
        when(mItemChecklistDao.countItemChecklistsById(5L)).thenReturn(1);
        when(mInventoryStatsCalculator.findAlertItemIds(anySet(), any(), anyInt()))
                .thenReturn(new ArrayList<>(Collections.singletonList(1L)));
        // nothing checked: regeneration is a no-op (zero side effects are
        // asserted in ItemChecklistServiceTest) but the append must still run
        when(mItemChecklistService.uncheckItemIds(eq(5L), anyList())).thenReturn(0);
        when(mItemChecklistService.addItemIds(eq(5L), anyList())).thenReturn(1);

        GenerateShoppingListCmd cmd = new GenerateShoppingListCmd(mProvider);
        GenerateShoppingListCmd.ShoppingListResult result = cmd.execute().blockingGet();

        assertEquals(5L, result.checklistId);
        assertEquals(1, result.addedCount);
        verify(mItemChecklistService).uncheckItemIds(eq(5L), anyList());
        verify(mItemChecklistService).addItemIds(eq(5L), anyList());
    }

    /**
     * Runs every command on the calling thread so the blockingGet chains are
     * deterministic without extra synchronization.
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
