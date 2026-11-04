package m.co.rh.id.a_personal_stuff.item_lending.provider.command;

import androidx.work.ExistingWorkPolicy;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.aprovider.Provider;

public class UpdateItemLendingCmd extends NewItemLendingCmd {

    public UpdateItemLendingCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<ItemLendingState> execute(ItemLendingState itemLendingState) {
        return Single.fromCallable(() -> {
                    mItemLendingDao.updateItemLending(itemLendingState);
                    ItemLendingState clone = itemLendingState.clone();
                    if (isReturnWorkNeeded(clone.getItemLending())) {
                        // REPLACE atomically cancels the previously scheduled work and
                        // enqueues a fresh request, so an updated due date takes
                        // effect. (cancelUniqueWork + KEEP would race: KEEP can keep
                        // the still-pending old work and discard the new schedule.)
                        mWorkManager.enqueueUniqueWork(clone.getItemLending().taskId,
                                ExistingWorkPolicy.REPLACE, buildWorkRequest(clone.getItemLending()));
                    } else {
                        // the lending was returned, or its due date was removed or is
                        // already past: no reminder must survive
                        mWorkManager.cancelUniqueWork(clone.getItemLending().taskId);
                    }
                    mItemLendingChangeNotifier.itemLendingUpdated(clone);
                    return itemLendingState;
                }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
