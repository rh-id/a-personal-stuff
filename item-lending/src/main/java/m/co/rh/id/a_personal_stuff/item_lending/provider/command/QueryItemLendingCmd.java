package m.co.rh.id.a_personal_stuff.item_lending.provider.command;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLending;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.aprovider.Provider;

public class QueryItemLendingCmd {
    private ExecutorService mExecutorService;
    private ItemLendingDao mItemLendingDao;

    public QueryItemLendingCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemLendingDao = provider.get(ItemLendingDao.class);
    }

    public Single<ItemLendingState> findItemLendingStateById(long id) {
        return Single.fromCallable(() ->
                mItemLendingDao.findItemLendingStateById(id)).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<List<ItemLending>> findItemLendingByItemId(long itemId) {
        return Single.fromCallable(() ->
                mItemLendingDao.findItemLendingByItemId(itemId)).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<List<ItemLendingState>> findItemLendingStateByItemId(long itemId) {
        return Single.fromCallable(() ->
                mItemLendingDao.findItemLendingStateByItemIdWithLimit(itemId, Integer.MAX_VALUE))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<Integer> countOpenLendingsByItemId(long itemId) {
        return Single.fromCallable(() ->
                mItemLendingDao.countOpenLendingsByItemId(itemId)).subscribeOn(Schedulers.from(mExecutorService));
    }
}
