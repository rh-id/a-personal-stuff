package m.co.rh.id.a_personal_stuff.item_checklist.provider.component;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

public class ItemChecklistEventHandler implements ProviderDisposable {

    private static final String TAG = ItemChecklistEventHandler.class.getName();

    private ExecutorService mExecutorService;
    private ItemChangeNotifier mItemChangeNotifier;
    private ItemChecklistDao mItemChecklistDao;
    private ILogger mLogger;
    private CompositeDisposable mCompositeDisposable;

    public ItemChecklistEventHandler(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
        mLogger = provider.get(ILogger.class);
        mCompositeDisposable = new CompositeDisposable();
        init();
    }

    private void init() {
        mCompositeDisposable.add(mItemChangeNotifier.getDeletedItemFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(
                        itemState -> mItemChecklistDao.deleteItemChecklistItemsByItemId(itemState.getItemId()),
                        throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)));
    }

    @Override
    public void dispose(Context context) {
        mCompositeDisposable.dispose();
    }
}
