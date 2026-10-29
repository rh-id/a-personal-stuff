package m.co.rh.id.a_personal_stuff.app.provider.component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Crash-safe startup sweep for cross-module child rows. When an item is
 * deleted, DeleteItemCmd deletes the item and then publishes on the in-memory
 * ItemChangeNotifier (PublishSubject, no replay); the per-module event
 * handlers delete that module's child rows on receipt. If the app crashes
 * between the delete and the event processing, child rows are orphaned
 * forever. This sweep reconciles them on every app start: per module DB it
 * deletes child rows whose item_id is no longer present in the base item
 * table.
 * <p>
 * The sweep runs as a constructor side effect (mirroring the
 * InventoryAlertScheduler startup pattern), so registering this class via
 * registerAsync triggers it on every app start from
 * MainApplication.onCreate's Provider.createProvider.
 */
public class OrphanSweep {

    private static final String TAG = OrphanSweep.class.getName();

    private ExecutorService mExecutorService;
    private ItemDao mItemDao;
    private ItemUsageDao mItemUsageDao;
    private ItemPurchaseDao mItemPurchaseDao;
    private ItemMaintenanceDao mItemMaintenanceDao;
    private ItemReminderDao mItemReminderDao;
    private ItemChecklistDao mItemChecklistDao;
    private ILogger mLogger;

    public OrphanSweep(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemDao = provider.get(ItemDao.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
        mLogger = provider.get(ILogger.class);
        // startup side effect: run the sweep in the background on every
        // construction (construction happens on every app start, see class doc)
        sweepAsync().subscribe(() -> { },
                throwable -> mLogger.e(TAG, throwable.getMessage(), throwable));
    }

    /**
     * Runs {@link #sweep()} on the shared background executor. Private: the
     * constructor subscription is the only caller; tests use {@link #sweep()}.
     */
    private Completable sweepAsync() {
        return Completable.fromAction(this::sweep)
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Synchronous sweep core, safe to call directly (e.g. from tests). It
     * never throws: a failed read or a failing module DB is logged and the
     * rest of the sweep continues, so app startup can never crash because of
     * it.
     */
    public void sweep() {
        try {
            List<Long> validItemIds = mItemDao.getAllItemIds();
            if (validItemIds == null || validItemIds.isEmpty()) {
                // an empty (possibly failed/partial) read of the valid id set
                // must never be treated as "delete all children": sweep nothing
                mLogger.e(TAG, "Orphan sweep skipped: " +
                        "item id read failed or returned empty, refusing to sweep");
                return;
            }
            Set<Long> validItemIdSet = new HashSet<>(validItemIds);
            int usageCount = sweepItemUsages(validItemIdSet);
            int purchaseCount = sweepItemPurchases(validItemIdSet);
            int maintenanceCount = sweepItemMaintenances(validItemIdSet);
            int reminderCount = sweepItemReminders(validItemIdSet);
            int checklistItemCount = sweepItemChecklistItems(validItemIdSet);
            mLogger.d(TAG, "Orphan sweep done: itemUsageOrphanItemIds=" + usageCount +
                    ", itemPurchaseOrphanItemIds=" + purchaseCount +
                    ", itemMaintenanceOrphanItemIds=" + maintenanceCount +
                    ", itemReminderOrphanItemIds=" + reminderCount +
                    ", itemChecklistItemOrphanItemIds=" + checklistItemCount);
        } catch (Throwable throwable) {
            mLogger.e(TAG, "Orphan sweep failed: " + throwable.getMessage(), throwable);
        }
    }

    private int sweepItemUsages(Set<Long> validItemIds) {
        try {
            return deleteOrphans(mItemUsageDao.findDistinctItemIds(), validItemIds,
                    chunk -> mItemUsageDao.deleteItemUsageStatesByItemIds(chunk));
        } catch (Throwable throwable) {
            mLogger.e(TAG, "Orphan sweep failed for item_usage: " +
                    throwable.getMessage(), throwable);
            return 0;
        }
    }

    private int sweepItemPurchases(Set<Long> validItemIds) {
        try {
            return deleteOrphans(mItemPurchaseDao.findDistinctItemIds(), validItemIds,
                    chunk -> mItemPurchaseDao.deleteItemPurchaseStatesByItemIds(chunk));
        } catch (Throwable throwable) {
            mLogger.e(TAG, "Orphan sweep failed for item_purchase: " +
                    throwable.getMessage(), throwable);
            return 0;
        }
    }

    private int sweepItemMaintenances(Set<Long> validItemIds) {
        try {
            return deleteOrphans(mItemMaintenanceDao.findDistinctItemIds(), validItemIds,
                    chunk -> mItemMaintenanceDao.deleteItemMaintenanceStatesByItemIds(chunk));
        } catch (Throwable throwable) {
            mLogger.e(TAG, "Orphan sweep failed for item_maintenance: " +
                    throwable.getMessage(), throwable);
            return 0;
        }
    }

    /**
     * Mirrors ItemReminderEventHandler minus the WorkManager unique-work
     * cancellation: deleting reminder rows without cancelling their work is
     * safe and intended because ItemReminderNotificationWorker no-ops when
     * the reminder row is gone.
     */
    private int sweepItemReminders(Set<Long> validItemIds) {
        try {
            return deleteOrphans(mItemReminderDao.findDistinctItemIds(), validItemIds,
                    chunk -> mItemReminderDao.deleteItemRemindersByItemIds(chunk));
        } catch (Throwable throwable) {
            mLogger.e(TAG, "Orphan sweep failed for item_reminder: " +
                    throwable.getMessage(), throwable);
            return 0;
        }
    }

    /**
     * Mirrors ItemChecklistEventHandler: only item_checklist_item rows are
     * deleted. Parent item_checklist rows have no item_id and intentionally
     * survive as empty checklists.
     */
    private int sweepItemChecklistItems(Set<Long> validItemIds) {
        try {
            return deleteOrphans(mItemChecklistDao.findDistinctItemIds(), validItemIds,
                    chunk -> mItemChecklistDao.deleteItemChecklistItemsByItemIds(chunk));
        } catch (Throwable throwable) {
            mLogger.e(TAG, "Orphan sweep failed for item_checklist_item: " +
                    throwable.getMessage(), throwable);
            return 0;
        }
    }

    private interface DeleteChunk {
        void delete(List<Long> orphanIdsChunk);
    }

    /**
     * Computes orphans = referenced child ids - valid item ids in Java (set
     * difference), then deletes them chunk by chunk with an
     * {@code DELETE ... WHERE item_id IN (:chunk)} statement. Chunking is
     * only ever applied to the IN side: a NOT IN chunking would be a
     * destructive union (each chunk's statement would delete valid rows whose
     * id happens to be in another chunk).
     */
    private int deleteOrphans(List<Long> referencedItemIds, Set<Long> validItemIds,
                              DeleteChunk deleteChunk) {
        Set<Long> orphanIdSet = new HashSet<>(referencedItemIds);
        orphanIdSet.removeAll(validItemIds);
        if (orphanIdSet.isEmpty()) {
            return 0;
        }
        List<Long> orphanIds = new ArrayList<>(orphanIdSet);
        int deleted = 0;
        for (List<Long> orphanIdsChunk : ItemDao.chunkIds(orphanIds)) {
            deleteChunk.delete(orphanIdsChunk);
            deleted += orphanIdsChunk.size();
        }
        return deleted;
    }
}
