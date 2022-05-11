package m.co.rh.id.a_personal_stuff.item_usage.provider.command;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.aprovider.Provider;

public class UpdateItemUsageCmd extends NewItemUsageCmd {

    public UpdateItemUsageCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<ItemUsageState> execute(ItemUsageState itemUsageState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
                    mItemUsageDao.updateItemUsage(itemUsageState);
                    mItemUsageChangeNotifier.itemUsageUpdated(itemUsageState.clone());
                    return itemUsageState;
                })
        );
    }
}
