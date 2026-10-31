package m.co.rh.id.a_personal_stuff.item_checklist.provider.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.aprovider.Provider;

/**
 * Unit tests for the shared checklist mutation service: every checklist
 * mutation (create / update / delete of a checklist, add / check-off /
 * uncheck / delete of its items) that the checklist Cmds and the app-level
 * shopping-list orchestrator both delegate to.
 */
public class ItemChecklistServiceTest {

    private ItemChecklistDao mItemChecklistDao;
    private ItemChecklistChangeNotifier mItemChecklistChangeNotifier;
    private ItemChecklistService mItemChecklistService;

    @Before
    public void setUp() {
        mItemChecklistDao = mock(ItemChecklistDao.class);
        mItemChecklistChangeNotifier = mock(ItemChecklistChangeNotifier.class);
        Provider provider = mock(Provider.class);
        when(provider.get(ItemChecklistDao.class)).thenReturn(mItemChecklistDao);
        when(provider.get(ItemChecklistChangeNotifier.class))
                .thenReturn(mItemChecklistChangeNotifier);
        mItemChecklistService = new ItemChecklistService(provider);
    }

    @Test
    public void createChecklistInsertsFiresAddedAndReturnsState() {
        ItemChecklistState state = new ItemChecklistState();
        state.setTitle("Groceries");

        ItemChecklistState returned = mItemChecklistService.createChecklist(state);

        // the same instance is returned so the caller sees the
        // Room-populated id (mirrors the DAO contract)
        assertSame(state, returned);
        verify(mItemChecklistDao).insertItemChecklist(state);
        ArgumentCaptor<ItemChecklistState> addedCaptor =
                ArgumentCaptor.forClass(ItemChecklistState.class);
        verify(mItemChecklistChangeNotifier).checklistAdded(addedCaptor.capture());
        // the notifier receives a clone: subscribers must not be able to
        // mutate the caller's state through the event
        ItemChecklistState notified = addedCaptor.getValue();
        assertNotSame(state, notified);
        assertNotSame(state.getItemChecklist(), notified.getItemChecklist());
        assertEquals("Groceries", notified.getTitle());
    }

    @Test
    public void addItemIdsSkipsExistingInsertsNewAndBumpsNotifies() {
        // id 1 is already on the checklist: only 2 and 3 must be inserted
        when(mItemChecklistDao.findExistingItemIds(5L))
                .thenReturn(new ArrayList<>(Collections.singletonList(1L)));
        Date oldUpdatedDateTime = new Date(0L);
        when(mItemChecklistDao.findItemChecklistStateById(5L))
                .thenReturn(stateWithChecklist(5L, oldUpdatedDateTime));

        int added = mItemChecklistService.addItemIds(5L, Arrays.asList(1L, 2L, 3L));

        assertEquals(2, added);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemChecklistItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mItemChecklistDao).insertItemChecklistItems(itemsCaptor.capture());
        List<ItemChecklistItem> inserted = itemsCaptor.getValue();
        assertEquals(2, inserted.size());
        for (ItemChecklistItem item : inserted) {
            assertEquals(5L, item.itemChecklistId.longValue());
            assertNull(item.checkedDateTime);
            assertTrue(item.itemId == 2L || item.itemId == 3L);
        }
        // the checklist row changed even though it was not inserted itself:
        // its timestamp is bumped and open detail pages are notified
        ArgumentCaptor<ItemChecklist> checklistCaptor =
                ArgumentCaptor.forClass(ItemChecklist.class);
        verify(mItemChecklistDao).updateItemChecklist(checklistCaptor.capture());
        assertTrue(checklistCaptor.getValue().updatedDateTime.after(oldUpdatedDateTime));
        verify(mItemChecklistChangeNotifier).checklistUpdated(any(ItemChecklistState.class));
    }

    @Test
    public void addItemIdsWithNothingNewDoesNotTouchDaoOrNotifier() {
        // every requested id is already on the checklist
        when(mItemChecklistDao.findExistingItemIds(5L))
                .thenReturn(new ArrayList<>(Arrays.asList(1L, 2L)));

        int added = mItemChecklistService.addItemIds(5L, Arrays.asList(1L, 2L));

        assertEquals(0, added);
        verify(mItemChecklistDao, never()).insertItemChecklistItems(anyList());
        verify(mItemChecklistDao, never()).findItemChecklistStateById(anyLong());
        verify(mItemChecklistDao, never()).updateItemChecklist(any(ItemChecklist.class));
        verify(mItemChecklistChangeNotifier, never()).checklistUpdated(any(ItemChecklistState.class));
    }

    @Test
    public void uncheckItemIdsBumpsAndNotifiesWhenRowsCleared() {
        Date oldUpdatedDateTime = new Date(0L);
        ItemChecklistState existing = stateWithChecklist(5L, oldUpdatedDateTime);
        when(mItemChecklistDao.findItemChecklistStateById(5L)).thenReturn(existing);
        when(mItemChecklistDao.uncheckItems(eq(5L), anyList())).thenReturn(1);

        int unchecked = mItemChecklistService.uncheckItemIds(5L, Arrays.asList(1L, 2L));

        assertEquals(1, unchecked);
        verify(mItemChecklistDao).uncheckItems(eq(5L), anyList());
        ArgumentCaptor<ItemChecklist> checklistCaptor =
                ArgumentCaptor.forClass(ItemChecklist.class);
        verify(mItemChecklistDao).updateItemChecklist(checklistCaptor.capture());
        assertTrue(checklistCaptor.getValue().updatedDateTime.after(oldUpdatedDateTime));
        // the notifier state must be a fresh re-read clone, not the caller's
        // mutated state instance
        ArgumentCaptor<ItemChecklistState> updatedCaptor =
                ArgumentCaptor.forClass(ItemChecklistState.class);
        verify(mItemChecklistChangeNotifier).checklistUpdated(updatedCaptor.capture());
        assertNotSame(existing, updatedCaptor.getValue());
        assertEquals(5L, updatedCaptor.getValue().getChecklistId().longValue());
    }

    @Test
    public void uncheckItemIdsWithNothingClearedDoesNotTouchDaoOrNotifier() {
        when(mItemChecklistDao.uncheckItems(eq(5L), anyList())).thenReturn(0);

        int unchecked = mItemChecklistService.uncheckItemIds(5L, Collections.singletonList(1L));

        assertEquals(0, unchecked);
        // no row changed: no state re-read, no timestamp bump, no notifier event
        verify(mItemChecklistDao, never()).findItemChecklistStateById(anyLong());
        verify(mItemChecklistDao, never()).updateItemChecklist(any(ItemChecklist.class));
        verify(mItemChecklistChangeNotifier, never()).checklistUpdated(any(ItemChecklistState.class));
    }

    @Test
    public void updateChecklistBumpsTimestampSavesAndNotifiesWithClone() {
        Date oldUpdatedDateTime = new Date(0L);
        ItemChecklistState state = stateWithChecklist(5L, oldUpdatedDateTime);

        ItemChecklistState returned = mItemChecklistService.updateChecklist(state);

        assertSame(state, returned);
        assertTrue(state.getUpdatedDateTime().after(oldUpdatedDateTime));
        verify(mItemChecklistDao).updateItemChecklist(state);
        ArgumentCaptor<ItemChecklistState> updatedCaptor =
                ArgumentCaptor.forClass(ItemChecklistState.class);
        verify(mItemChecklistChangeNotifier).checklistUpdated(updatedCaptor.capture());
        // the notifier receives a clone taken AFTER the bump: subscribers see
        // the new timestamp but cannot mutate the caller's state
        ItemChecklistState notified = updatedCaptor.getValue();
        assertNotSame(state, notified);
        assertNotSame(state.getItemChecklist(), notified.getItemChecklist());
        assertTrue(notified.getUpdatedDateTime().after(oldUpdatedDateTime));
    }

    @Test
    public void deleteChecklistDeletesAndNotifiesWithClone() {
        ItemChecklistState state = stateWithChecklist(5L, new Date());

        ItemChecklistState returned = mItemChecklistService.deleteChecklist(state);

        assertSame(state, returned);
        verify(mItemChecklistDao).deleteItemChecklist(state);
        ArgumentCaptor<ItemChecklistState> deletedCaptor =
                ArgumentCaptor.forClass(ItemChecklistState.class);
        verify(mItemChecklistChangeNotifier).checklistDeleted(deletedCaptor.capture());
        ItemChecklistState notified = deletedCaptor.getValue();
        assertNotSame(state, notified);
        assertNotSame(state.getItemChecklist(), notified.getItemChecklist());
        assertEquals("Shopping list", notified.getTitle());
    }

    @Test
    public void updateChecklistItemBumpsParentUpdatesBothAndNotifiesWithFreshClone() {
        Date oldUpdatedDateTime = new Date(0L);
        ItemChecklistState existing = stateWithChecklist(5L, oldUpdatedDateTime);
        when(mItemChecklistDao.findItemChecklistStateById(5L)).thenReturn(existing);
        ItemChecklistItem item = new ItemChecklistItem();
        item.itemChecklistId = 5L;

        ItemChecklistItem returned = mItemChecklistService.updateChecklistItem(item);

        assertSame(item, returned);
        // the parent row is bumped inside the same @Transaction update
        ArgumentCaptor<ItemChecklist> checklistCaptor =
                ArgumentCaptor.forClass(ItemChecklist.class);
        verify(mItemChecklistDao).updateItemChecklistItem(eq(item), checklistCaptor.capture());
        assertTrue(checklistCaptor.getValue().updatedDateTime.after(oldUpdatedDateTime));
        // the notifier state is the fresh re-read clone, not the caller's
        // mutated instance
        ArgumentCaptor<ItemChecklistState> updatedCaptor =
                ArgumentCaptor.forClass(ItemChecklistState.class);
        verify(mItemChecklistChangeNotifier).checklistItemUpdated(eq(item), updatedCaptor.capture());
        assertNotSame(existing, updatedCaptor.getValue());
        assertEquals(5L, updatedCaptor.getValue().getChecklistId().longValue());
        // the single-arg update is only the missing-parent fallback
        verify(mItemChecklistDao, never()).updateItemChecklistItem(item);
    }

    @Test
    public void updateChecklistItemWithMissingParentFallsBackToBareUpdateWithoutNotifier() {
        // the DAO returns a state whose checklist row is gone
        when(mItemChecklistDao.findItemChecklistStateById(5L))
                .thenReturn(mock(ItemChecklistState.class));
        ItemChecklistItem item = new ItemChecklistItem();
        item.itemChecklistId = 5L;

        ItemChecklistItem returned = mItemChecklistService.updateChecklistItem(item);

        assertSame(item, returned);
        verify(mItemChecklistDao).updateItemChecklistItem(item);
        // no parent row: no bump, no @Transaction update, no notifier event
        verify(mItemChecklistDao, never()).updateItemChecklistItem(
                any(ItemChecklistItem.class), any(ItemChecklist.class));
        verify(mItemChecklistDao, never()).updateItemChecklist(any(ItemChecklist.class));
        verify(mItemChecklistChangeNotifier, never()).checklistItemUpdated(
                any(ItemChecklistItem.class), any(ItemChecklistState.class));
    }

    @Test
    public void deleteChecklistItemBumpsParentDeletesThenReReadsAndNotifies() {
        Date oldUpdatedDateTime = new Date(0L);
        ItemChecklistState existing = stateWithChecklist(5L, oldUpdatedDateTime);
        ItemChecklistState fresh = stateWithChecklist(5L, new Date());
        // 1st read loads the parent, 2nd read happens AFTER the deletion
        when(mItemChecklistDao.findItemChecklistStateById(5L)).thenReturn(existing, fresh);
        ItemChecklistItem item = new ItemChecklistItem();
        item.itemChecklistId = 5L;

        ItemChecklistItem returned = mItemChecklistService.deleteChecklistItem(item);

        assertSame(item, returned);
        InOrder inOrder = inOrder(mItemChecklistDao, mItemChecklistChangeNotifier);
        // the parent is bumped BEFORE the row is deleted
        ArgumentCaptor<ItemChecklist> bumpCaptor =
                ArgumentCaptor.forClass(ItemChecklist.class);
        inOrder.verify(mItemChecklistDao).updateItemChecklist(bumpCaptor.capture());
        assertTrue(bumpCaptor.getValue().updatedDateTime.after(oldUpdatedDateTime));
        inOrder.verify(mItemChecklistDao).deleteItemChecklistItem(item);
        // the notifier fires after the deletion with the re-read clone
        ArgumentCaptor<ItemChecklistState> deletedCaptor =
                ArgumentCaptor.forClass(ItemChecklistState.class);
        inOrder.verify(mItemChecklistChangeNotifier)
                .checklistItemDeleted(eq(item), deletedCaptor.capture());
        ItemChecklistState notified = deletedCaptor.getValue();
        assertNotSame(fresh, notified);
        assertNotSame(fresh.getItemChecklist(), notified.getItemChecklist());
        assertEquals(5L, notified.getChecklistId().longValue());
    }

    private ItemChecklistState stateWithChecklist(long id, Date updatedDateTime) {
        ItemChecklistState state = new ItemChecklistState();
        ItemChecklist checklist = new ItemChecklist();
        checklist.id = id;
        checklist.title = "Shopping list";
        checklist.updatedDateTime = updatedDateTime;
        state.updateItemChecklist(checklist);
        return state;
    }
}
