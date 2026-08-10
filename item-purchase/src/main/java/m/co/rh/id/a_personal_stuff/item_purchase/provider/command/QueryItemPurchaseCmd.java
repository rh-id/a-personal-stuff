package m.co.rh.id.a_personal_stuff.item_purchase.provider.command;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.aprovider.Provider;

public class QueryItemPurchaseCmd {
    private ExecutorService mExecutorService;
    private ItemPurchaseDao mItemPurchaseDao;

    public QueryItemPurchaseCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
    }

    public Single<ItemPurchaseState> findItemPurchaseStateById(long id) {
        return Single.fromCallable(() ->
                mItemPurchaseDao.findItemPurchaseStateById(id)).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<List<ItemPurchase>> findItemPurchaseByItemId(long itemId) {
        return Single.fromCallable(() ->
                mItemPurchaseDao.findItemPurchaseByItemId(itemId)).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<List<ItemPurchaseState>> findItemPurchaseStateByItemId(long itemId) {
        return Single.fromCallable(() ->
                mItemPurchaseDao.findItemPurchaseStateByItemIdWithLimit(itemId, Integer.MAX_VALUE))
                .subscribeOn(Schedulers.from(mExecutorService));
    }
}
