package m.co.rh.id.a_personal_stuff.item_checklist.provider.component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.aprovider.Provider;

/**
 * Hosts ALL checklist mutation logic (project invariant): every create,
 * update and delete of a checklist or one of its items — the exact DAO call
 * sequence, the {@code updatedDateTime} bumping and the change
 * notifications — lives in this service, so the checklist Cmds
 * ({@code NewItemChecklistCmd}, {@code AddItemChecklistItemCmd},
 * {@code UpdateItemChecklistCmd}, {@code DeleteItemChecklistCmd},
 * {@code UpdateItemChecklistItemCmd}, {@code DeleteItemChecklistItemCmd})
 * and the app-level shopping-list orchestrator
 * ({@code app/.../GenerateShoppingListCmd}) are thin Rx adapters that only
 * delegate here, and no Cmd ever invokes another Cmd. Read-only queries
 * stay in the screen-scoped Cmds ({@code PagedItemChecklistCmd},
 * {@code QueryItemChecklistCmd}).
 * <p>
 * All methods are synchronous and block on Room — callers MUST invoke them
 * off the main thread. In practice every caller is a Cmd whose
 * {@code Single} is subscribed on the shared executor, so the service itself
 * stays executor-agnostic and thread-safe (stateless beyond its
 * thread-safe DAO/notifier dependencies).
 */
public class ItemChecklistService {

    private ItemChecklistDao mItemChecklistDao;
    private ItemChecklistChangeNotifier mItemChecklistChangeNotifier;

    public ItemChecklistService(Provider provider) {
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
        mItemChecklistChangeNotifier = provider.get(ItemChecklistChangeNotifier.class);
    }

    /**
     * Inserts the checklist (with any attached items) and fires
     * {@code checklistAdded}. Returns the same state instance with the
     * Room-generated id populated on its checklist row.
     */
    public ItemChecklistState createChecklist(ItemChecklistState state) {
        mItemChecklistDao.insertItemChecklist(state);
        mItemChecklistChangeNotifier.checklistAdded(state.clone());
        return state;
    }

    /**
     * Appends the given item ids as unchecked rows of one checklist,
     * skipping ids already present on it, then bumps the checklist's
     * {@code updatedDateTime} and fires {@code checklistUpdated} so any open
     * detail page re-renders. Returns how many rows were actually added
     * (zero when every id was already on the checklist — nothing is bumped
     * or notified in that case).
     */
    public int addItemIds(long checklistId, List<Long> itemIds) {
        // Filter out ids already present
        List<Long> existingItemIds = mItemChecklistDao.findExistingItemIds(checklistId);
        List<Long> newItemIds = itemIds.stream()
                .filter(id -> !existingItemIds.contains(id))
                .collect(Collectors.toList());

        if (newItemIds.isEmpty()) {
            return 0;
        }

        // Build ItemChecklistItem rows
        List<ItemChecklistItem> items = new ArrayList<>();
        for (Long itemId : newItemIds) {
            ItemChecklistItem item = new ItemChecklistItem();
            item.itemChecklistId = checklistId;
            item.itemId = itemId;
            item.checkedDateTime = null;
            item.createdDateTime = new Date();
            items.add(item);
        }

        // Insert items
        mItemChecklistDao.insertItemChecklistItems(items);

        // Bump checklist updatedDateTime
        ItemChecklistState state = mItemChecklistDao.findItemChecklistStateById(checklistId);
        if (state == null) {
            // the checklist row can disappear mid-flight (deleted between
            // the item insert and this re-read); the rows are inserted, so
            // skip the bump/notification instead of crashing, mirroring the
            // uncheckItemIds guard
            return newItemIds.size();
        }
        ItemChecklist checklist = state.getItemChecklist();
        if (checklist != null) {
            checklist.updatedDateTime = new Date();
            mItemChecklistDao.updateItemChecklist(checklist);
            ItemChecklistState freshState = mItemChecklistDao.findItemChecklistStateById(checklistId);
            if (freshState != null) {
                mItemChecklistChangeNotifier.checklistUpdated(freshState.clone());
            }
        }

        return newItemIds.size();
    }

    /**
     * Moves the given items' rows of one checklist back to the open section
     * (regeneration for the designated shopping list): a re-tap must move
     * already-checked items back to unchecked, otherwise a fully-checked
     * stale list would never re-surface its restock entries (the checklist
     * UI sorts checked rows away). Only when at least one row was actually
     * cleared is the checklist's {@code updatedDateTime} bumped and
     * {@code checklistUpdated} fired. Returns the number of rows unchecked.
     */
    public int uncheckItemIds(long checklistId, List<Long> itemIds) {
        int uncheckedCount = mItemChecklistDao.uncheckItems(checklistId, itemIds);
        if (uncheckedCount > 0) {
            // the checklist row changes even though no item row was inserted
            ItemChecklistState state = mItemChecklistDao.findItemChecklistStateById(checklistId);
            if (state == null) {
                return uncheckedCount;
            }
            ItemChecklist checklist = state.getItemChecklist();
            if (checklist != null) {
                checklist.updatedDateTime = new Date();
                mItemChecklistDao.updateItemChecklist(checklist);
                ItemChecklistState freshState = mItemChecklistDao.findItemChecklistStateById(checklistId);
                if (freshState != null) {
                    mItemChecklistChangeNotifier.checklistUpdated(freshState.clone());
                }
            }
        }
        return uncheckedCount;
    }

    /**
     * Persists the caller's edited checklist (title/description already
     * validated by the UI): the {@code updatedDateTime} is bumped so list
     * ordering reflects the last edit, then {@code checklistUpdated} fires
     * with a clone so any open detail page re-renders. Returns the same
     * state instance.
     */
    public ItemChecklistState updateChecklist(ItemChecklistState state) {
        state.setUpdatedDateTime(new Date());
        mItemChecklistDao.updateItemChecklist(state);
        mItemChecklistChangeNotifier.checklistUpdated(state.clone());
        return state;
    }

    /**
     * Deletes the checklist row together with its item rows (one DAO
     * transaction) and fires {@code checklistDeleted} with a clone.
     * Returns the same state instance.
     */
    public ItemChecklistState deleteChecklist(ItemChecklistState state) {
        mItemChecklistDao.deleteItemChecklist(state);
        mItemChecklistChangeNotifier.checklistDeleted(state.clone());
        return state;
    }

    /**
     * Persists one edited item row (e.g. a check-off) and bumps the parent
     * checklist's {@code updatedDateTime} in the same {@code @Transaction}
     * update so the checklist list keeps sorting it as recently touched.
     * The state is re-read after the update so {@code checklistItemUpdated}
     * carries the fresh parent to any open detail page. When the parent row
     * can no longer be found only the bare item row is updated — no
     * notification — so a check-off is not lost when its checklist
     * disappeared mid-flight.
     */
    public ItemChecklistItem updateChecklistItem(ItemChecklistItem item) {
        // Load checklist to bump its updatedDateTime
        ItemChecklistState state = mItemChecklistDao.findItemChecklistStateById(item.itemChecklistId);
        ItemChecklist checklist = state == null ? null : state.getItemChecklist();
        if (checklist != null) {
            checklist.updatedDateTime = new Date();
            // Use the @Transaction method to update both
            mItemChecklistDao.updateItemChecklistItem(item, checklist);
            ItemChecklistState freshState = mItemChecklistDao.findItemChecklistStateById(item.itemChecklistId);
            if (freshState != null) {
                mItemChecklistChangeNotifier.checklistItemUpdated(item, freshState.clone());
            }
        } else {
            // Fallback if checklist not found
            mItemChecklistDao.updateItemChecklistItem(item);
        }
        return item;
    }

    /**
     * Deletes one item row and bumps the parent checklist's
     * {@code updatedDateTime} first, so the checklist list reflects the
     * touch even though the checklist row itself was not edited. The state
     * is re-read AFTER the deletion so {@code checklistItemDeleted} carries
     * the removed row plus the fresh parent to any open detail page. When
     * the parent row can no longer be found the deletion still proceeds —
     * no notification — mirroring the {@code updateChecklistItem} guard.
     */
    public ItemChecklistItem deleteChecklistItem(ItemChecklistItem item) {
        // Bump checklist updatedDateTime
        ItemChecklistState state = mItemChecklistDao.findItemChecklistStateById(item.itemChecklistId);
        if (state != null) {
            ItemChecklist checklist = state.getItemChecklist();
            if (checklist != null) {
                checklist.updatedDateTime = new Date();
                mItemChecklistDao.updateItemChecklist(checklist);
            }
        }

        // Delete the item
        mItemChecklistDao.deleteItemChecklistItem(item);

        state = mItemChecklistDao.findItemChecklistStateById(item.itemChecklistId);
        if (state != null) {
            mItemChecklistChangeNotifier.checklistItemDeleted(item, state.clone());
        }

        return item;
    }
}
