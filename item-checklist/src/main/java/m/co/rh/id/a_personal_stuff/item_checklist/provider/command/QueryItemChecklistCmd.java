package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistProgress;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.aprovider.Provider;

public class QueryItemChecklistCmd {
    private ExecutorService mExecutorService;
    private ItemChecklistDao mItemChecklistDao;

    public QueryItemChecklistCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
    }

    /**
     * Loads one checklist with its item rows. Completes EMPTY when the id is
     * stale (the checklist row is gone — the DAO answers {@code null}), so
     * stale-id consumers skip cleanly instead of erroring; wraps
     * {@link ItemChecklistDao#findItemChecklistStateById(long)} directly.
     */
    public Maybe<ItemChecklistState> findItemChecklistStateById(long id) {
        return Maybe.fromCallable(() ->
                mItemChecklistDao.findItemChecklistStateById(id)).subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Cheap DB-backed existence check for an id that may be stale (e.g. the
     * settings-stored designated shopping-list id after its checklist was
     * deleted). Wraps {@link ItemChecklistDao#countItemChecklistsById(long)}
     * because {@link #findItemChecklistStateById(long)} materializes the
     * full checklist + item-row state just to answer presence — this COUNT
     * query answers it as a plain boolean.
     */
    public Single<Boolean> existsById(long id) {
        return Single.fromCallable(() ->
                mItemChecklistDao.countItemChecklistsById(id) > 0)
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<List<ItemChecklistItem>> findItemsByChecklistId(long checklistId) {
        return Single.fromCallable(() ->
                mItemChecklistDao.findItemChecklistItemsByChecklistId(checklistId)).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<List<ItemChecklistProgress>> findAllProgress() {
        return Single.fromCallable(() ->
                mItemChecklistDao.findAllItemChecklistProgress()).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<ItemChecklistProgress> findProgressByChecklistId(long checklistId) {
        return Single.fromCallable(() -> {
            ItemChecklistProgress progress =
                    mItemChecklistDao.findItemChecklistProgressByChecklistId(checklistId);
            if (progress == null) {
                progress = new ItemChecklistProgress(checklistId, 0, 0);
            }
            return progress;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<Integer> countChecklistsByItemId(long itemId) {
        return Single.fromCallable(() ->
                mItemChecklistDao.countItemChecklistItemsByItemId(itemId)).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<List<ItemChecklistState>> findChecklistStatesByItemId(long itemId) {
        return Single.fromCallable(() ->
                mItemChecklistDao.findItemChecklistStatesByItemId(itemId)).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<List<ItemChecklistState>> findChecklistStatesNotContainingItem(long itemId, String search) {
        return Single.fromCallable(() ->
                mItemChecklistDao.findItemChecklistStatesNotContainingItem(itemId, search)).subscribeOn(Schedulers.from(mExecutorService));
    }
}
