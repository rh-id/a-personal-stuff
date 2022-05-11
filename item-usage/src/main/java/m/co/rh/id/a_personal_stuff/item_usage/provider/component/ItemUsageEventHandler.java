package m.co.rh.id.a_personal_stuff.item_usage.provider.component;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

public class ItemUsageEventHandler implements ProviderDisposable {

    private ExecutorService mExecutorService;
    private ItemChangeNotifier mItemChangeNotifier;
    private ItemUsageDao mItemUsageDao;
    private CompositeDisposable mCompositeDisposable;

    public ItemUsageEventHandler(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mCompositeDisposable = new CompositeDisposable();
        init();
    }

    private void init() {
        mCompositeDisposable.add(mItemChangeNotifier.getDeletedItemFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(itemState -> mItemUsageDao.deleteItemUsageStatesByItemId(itemState.getItemId())));
    }

    @Override
    public void dispose(Context context) {
        mCompositeDisposable.dispose();
    }
}
