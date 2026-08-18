package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import java.util.List;
import java.util.concurrent.ExecutorService;

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

    public Single<ItemChecklistState> findItemChecklistStateById(long id) {
        return Single.fromCallable(() ->
                mItemChecklistDao.findItemChecklistStateById(id)).subscribeOn(Schedulers.from(mExecutorService));
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
