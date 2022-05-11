package m.co.rh.id.a_personal_stuff.item_maintenance.provider.component;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

public class ItemMaintenanceEventHandler implements ProviderDisposable {

    private ExecutorService mExecutorService;
    private ItemChangeNotifier mItemChangeNotifier;
    private ItemMaintenanceDao mItemMaintenanceDao;
    private CompositeDisposable mCompositeDisposable;

    public ItemMaintenanceEventHandler(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mCompositeDisposable = new CompositeDisposable();
        init();
    }

    private void init() {
        mCompositeDisposable.add(mItemChangeNotifier.getDeletedItemFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(itemState -> mItemMaintenanceDao.deleteItemMaintenanceStatesByItemId(itemState.getItemId()))
        );
    }

    @Override
    public void dispose(Context context) {
        mCompositeDisposable.dispose();
    }
}
