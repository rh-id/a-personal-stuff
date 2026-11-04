package m.co.rh.id.a_personal_stuff.item_lending.provider.command;

import java.util.concurrent.ExecutorService;

import androidx.work.WorkManager;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.a_personal_stuff.item_lending.provider.notifier.ItemLendingChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class DeleteItemLendingCmd {
    private ExecutorService mExecutorService;
    private WorkManager mWorkManager;
    private ItemLendingDao mItemLendingDao;
    private ItemLendingChangeNotifier mItemLendingChangeNotifier;

    public DeleteItemLendingCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mWorkManager = provider.get(WorkManager.class);
        mItemLendingDao = provider.get(ItemLendingDao.class);
        mItemLendingChangeNotifier = provider.get(ItemLendingChangeNotifier.class);
    }

    public Single<ItemLendingState> execute(ItemLendingState itemLendingState) {
        return Single.fromCallable(() -> {
            mItemLendingDao.deleteItemLending(itemLendingState);
            mWorkManager.cancelUniqueWork(itemLendingState.getItemLending().taskId);
            mItemLendingChangeNotifier.itemLendingDeleted(itemLendingState.clone());
            return itemLendingState;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
