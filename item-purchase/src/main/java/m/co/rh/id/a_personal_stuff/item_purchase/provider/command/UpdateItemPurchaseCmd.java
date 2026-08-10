package m.co.rh.id.a_personal_stuff.item_purchase.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.aprovider.Provider;

public class UpdateItemPurchaseCmd extends NewItemPurchaseCmd {

    public UpdateItemPurchaseCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<ItemPurchaseState> execute(ItemPurchaseState itemPurchaseState) {
        return Single.fromCallable(() -> {
                    mItemPurchaseDao.updateItemPurchase(itemPurchaseState);
                    mItemPurchaseChangeNotifier.itemPurchaseUpdated(itemPurchaseState.clone());
                    return itemPurchaseState;
                }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
