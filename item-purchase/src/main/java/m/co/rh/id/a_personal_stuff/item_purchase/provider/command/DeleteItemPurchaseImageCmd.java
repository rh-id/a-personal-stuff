package m.co.rh.id.a_personal_stuff.item_purchase.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class DeleteItemPurchaseImageCmd {
    private ExecutorService mExecutorService;
    private ItemPurchaseDao mItemPurchaseDao;
    private ItemPurchaseChangeNotifier mItemPurchaseChangeNotifier;

    public DeleteItemPurchaseImageCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
        mItemPurchaseChangeNotifier = provider.get(ItemPurchaseChangeNotifier.class);
    }

    public Single<ItemPurchaseImage> execute(ItemPurchaseImage itemPurchaseImage) {
        return Single.fromCallable(() -> {
            mItemPurchaseDao.deleteItemPurchaseImage(itemPurchaseImage);
            mItemPurchaseChangeNotifier.imageDeleted(itemPurchaseImage);
            return itemPurchaseImage;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
