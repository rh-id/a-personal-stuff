package m.co.rh.id.a_personal_stuff.item_lending.provider.component;

import android.content.Context;

import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLending;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

public class ItemLendingEventHandler implements ProviderDisposable {

    private static final String TAG = ItemLendingEventHandler.class.getName();

    private ExecutorService mExecutorService;
    private WorkManager mWorkManager;
    private ItemChangeNotifier mItemChangeNotifier;
    private ItemLendingDao mItemLendingDao;
    private ILogger mLogger;
    private CompositeDisposable mCompositeDisposable;

    public ItemLendingEventHandler(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mWorkManager = provider.get(WorkManager.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
        mItemLendingDao = provider.get(ItemLendingDao.class);
        mLogger = provider.get(ILogger.class);
        mCompositeDisposable = new CompositeDisposable();
        init();
    }

    private void init() {
        mCompositeDisposable.add(mItemChangeNotifier.getDeletedItemFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(
                        itemState -> {
                            // task ids are captured before the bulk delete so each
                            // removed lending's return-reminder work can be cancelled
                            List<ItemLending> itemLendingList = mItemLendingDao.findItemLendingByItemId(itemState.getItemId());
                            if (!itemLendingList.isEmpty()) {
                                mItemLendingDao.deleteItemLendingStatesByItemId(itemState.getItemId());
                                for (ItemLending itemLending : itemLendingList) {
                                    mWorkManager.cancelUniqueWork(itemLending.taskId);
                                }
                            }
                        },
                        throwable -> mLogger.e(TAG, throwable.getMessage(), throwable))
        );
    }

    @Override
    public void dispose(Context context) {
        mCompositeDisposable.dispose();
    }
}
