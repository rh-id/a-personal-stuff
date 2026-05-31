package m.co.rh.id.a_personal_stuff.item_usage.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.aprovider.Provider;

public class UpdateItemUsageCmd extends NewItemUsageCmd {

    public UpdateItemUsageCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<ItemUsageState> execute(ItemUsageState itemUsageState) {
        return Single.fromCallable(() -> {
                    mItemUsageDao.updateItemUsage(itemUsageState);
                    mItemUsageChangeNotifier.itemUsageUpdated(itemUsageState.clone());
                    return itemUsageState;
                }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
