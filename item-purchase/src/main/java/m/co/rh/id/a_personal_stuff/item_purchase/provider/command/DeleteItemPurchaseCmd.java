package m.co.rh.id.a_personal_stuff.item_purchase.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class DeleteItemPurchaseCmd {
    private ExecutorService mExecutorService;
    private ItemPurchaseDao mItemPurchaseDao;
    private ItemPurchaseChangeNotifier mItemPurchaseChangeNotifier;

    public DeleteItemPurchaseCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
        mItemPurchaseChangeNotifier = provider.get(ItemPurchaseChangeNotifier.class);
    }

    public Single<ItemPurchaseState> execute(ItemPurchaseState itemPurchaseState) {
        return Single.fromCallable(() -> {
            mItemPurchaseDao.deleteItemPurchase(itemPurchaseState);
            mItemPurchaseChangeNotifier.itemPurchaseDeleted(itemPurchaseState.clone());
            return itemPurchaseState;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
