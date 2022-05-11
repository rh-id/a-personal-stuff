package m.co.rh.id.a_personal_stuff.item_usage.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class DeleteItemUsageCmd {
    private ExecutorService mExecutorService;
    private ItemUsageDao mItemUsageDao;
    private ItemUsageChangeNotifier mItemUsageChangeNotifier;

    public DeleteItemUsageCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mItemUsageChangeNotifier = provider.get(ItemUsageChangeNotifier.class);
    }

    public Single<ItemUsageState> execute(ItemUsageState itemUsageState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mItemUsageDao.deleteItemUsage(itemUsageState);
            mItemUsageChangeNotifier.itemUsageDeleted(itemUsageState.clone());
            return itemUsageState;
        }));
    }
}
