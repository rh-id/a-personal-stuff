package m.co.rh.id.a_personal_stuff.app.provider.command;

import android.content.Context;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.component.InventoryStatsCalculator;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.AddItemChecklistItemCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.NewItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.aprovider.Provider;

/**
 * One-tap generation of the designated "Shopping list" checklist: the items
 * whose derived remaining stock (amount − Σusage + Σpurchase, matching the
 * item card badge) is at or below minAmount are appended to a single reusable
 * checklist, so restocking starts from the checklist screen instead of the
 * low-stock dashboard row. Check-off → stock update stays a manual step
 * (deferred to a follow-up issue).
 * <p>
 * The designated checklist is found-or-created on every run: the id is
 * persisted in {@link SettingsSharedPreferences} (NOT a backup contributor —
 * checklist titles survive a restore via
 * {@code ItemChecklistBackupContributor.importJson}, so a lost id adopts the
 * most recent same-title checklist once before creating a new one). All
 * blocking Room work runs as ONE {@link Single#fromCallable} on the shared
 * executor: the inner commands are joined with {@code blockingGet()} so the
 * sequence is a single synchronous unit, and the find-or-create section is
 * additionally locked because the shared pool never cancels a disposed
 * callable — two rapid taps would otherwise both pass the "no designated
 * list" check and create two same-title lists (the title column has no
 * unique constraint).
 * <p>
 * Accepted risks of the adopt-by-title heuristic, both judged cheaper than a
 * schema change:
 * <ul>
 *     <li>If the stored id is lost AND the device locale changed since the
 *     list was created, exact-title adoption fails and a second locale-titled
 *     list is created; the old list's entries are stranded but untouched.</li>
 *     <li>SQLite rowid reuse: after the designated checklist is deleted, a
 *     newly created unrelated checklist can receive the stored id and become
 *     the low-stock target. Benign — low-stock items are only ever appended,
 *     never removed.</li>
 * </ul>
 */
public class GenerateShoppingListCmd {

    /**
     * Serializes find-or-create across concurrent runs: a disposed-but-still
     * running earlier tap cannot be cancelled by the shared weighted pool,
     * so without this lock two rapid taps could each create a checklist.
     */
    private final Object mFindOrCreateLock = new Object();

    private Context mContext;
    private ExecutorService mExecutorService;
    private SettingsSharedPreferences mSettingsSharedPreferences;
    private InventoryStatsCalculator mInventoryStatsCalculator;
    private ItemChecklistDao mItemChecklistDao;
    private ItemChecklistChangeNotifier mItemChecklistChangeNotifier;
    private NewItemChecklistCmd mNewItemChecklistCmd;
    private AddItemChecklistItemCmd mAddItemChecklistItemCmd;

    public GenerateShoppingListCmd(Provider provider) {
        mContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mSettingsSharedPreferences = provider.get(SettingsSharedPreferences.class);
        mInventoryStatsCalculator = provider.get(InventoryStatsCalculator.class);
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
        mItemChecklistChangeNotifier = provider.get(ItemChecklistChangeNotifier.class);
        mNewItemChecklistCmd = provider.get(NewItemChecklistCmd.class);
        mAddItemChecklistItemCmd = provider.get(AddItemChecklistItemCmd.class);
    }

    /**
     * Computes the current low-stock items and appends the ones not yet on
     * the designated shopping list. Re-taps are idempotent: already-present
     * items are skipped by {@link AddItemChecklistItemCmd} and already-checked
     * ones are un-checked again, so a fully-checked stale list re-opens
     * instead of silently doing nothing. With no low-stock items the
     * designated list is not touched (let alone created).
     */
    public Single<ShoppingListResult> execute() {
        return Single.fromCallable(() -> {
            // leadDays only gates the expiry alert types and is irrelevant to
            // LOW_STOCK, but passing it matches the existing call-site
            // convention (see HomePage.navigateToAlertItems)
            List<Long> lowStockIds = mInventoryStatsCalculator.findAlertItemIds(
                    EnumSet.of(InventoryStatsCalculator.AlertType.LOW_STOCK),
                    new Date(), mSettingsSharedPreferences.getExpiryAlertLeadDays());
            if (lowStockIds.isEmpty()) {
                return new ShoppingListResult();
            }
            String localizedTitle = mContext.getString(R.string.shopping_list_title);
            long checklistId;
            synchronized (mFindOrCreateLock) {
                checklistId = findOrCreateChecklistId(localizedTitle);
            }
            regenerateCheckedItems(lowStockIds, checklistId);
            int added = mAddItemChecklistItemCmd.execute(checklistId, lowStockIds).blockingGet();
            ShoppingListResult result = new ShoppingListResult();
            result.checklistId = checklistId;
            result.addedCount = added;
            result.lowStockCount = lowStockIds.size();
            return result;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Resolves the designated shopping-list checklist id: a stored id that
     * still exists wins, then a one-time adoption of the most recent
     * same-title checklist, then creation. MUST be called under
     * {@link #mFindOrCreateLock} — see the class javadoc for why.
     */
    private long findOrCreateChecklistId(String localizedTitle) {
        long storedId = mSettingsSharedPreferences.getShoppingListChecklistId();
        if (storedId > 0 && mItemChecklistDao.countItemChecklistsById(storedId) > 0) {
            return storedId;
        }
        // the stored id is gone (checklist deleted, or a fresh restore where
        // settings prefs are not backup contributors while titles survive the
        // checklist import): adopt the most recent same-title checklist once,
        // so a restore keeps a single list instead of growing a new one per run
        ItemChecklist adopted = mItemChecklistDao.findItemChecklistByTitle(localizedTitle);
        if (adopted != null) {
            mSettingsSharedPreferences.setShoppingListChecklistId(adopted.id);
            return adopted.id;
        }
        ItemChecklistState newState = new ItemChecklistState();
        newState.setTitle(localizedTitle);
        ItemChecklistState created = mNewItemChecklistCmd.execute(newState).blockingGet();
        long newId = created.getChecklistId();
        mSettingsSharedPreferences.setShoppingListChecklistId(newId);
        return newId;
    }

    /**
     * Moves the low-stock items' rows of the designated list back to the open
     * section when they are already checked. Without this a fully-checked
     * stale list would never re-surface its restock entries (the checklist UI
     * sorts checked rows away), which is the dead-end this command exists to
     * avoid. Mirrors AddItemChecklistItemCmd's bump-and-notify so the list's
     * timestamp updates and any open detail page re-renders.
     */
    private void regenerateCheckedItems(List<Long> lowStockIds, long checklistId) {
        int uncheckedCount = mItemChecklistDao.uncheckItems(checklistId, lowStockIds);
        if (uncheckedCount > 0) {
            // same bump-and-fire pattern as AddItemChecklistItemCmd: the
            // checklist row changes even though no item row was inserted
            ItemChecklistState state = mItemChecklistDao.findItemChecklistStateById(checklistId);
            if (state == null) {
                return;
            }
            ItemChecklist checklist = state.getItemChecklist();
            if (checklist != null) {
                checklist.updatedDateTime = new Date();
                mItemChecklistDao.updateItemChecklist(checklist);
                ItemChecklistState freshState = mItemChecklistDao.findItemChecklistStateById(checklistId);
                mItemChecklistChangeNotifier.checklistUpdated(freshState.clone());
            }
        }
    }

    /**
     * Outcome of one generation run: where the designated list lives, how
     * many low-stock items were newly appended, and how many low-stock items
     * exist in total (zero means the dashboard row is stale and there is
     * nothing to shop for).
     */
    public static class ShoppingListResult {
        public long checklistId;
        public int addedCount;
        public int lowStockCount;
    }
}
