package m.co.rh.id.a_personal_stuff.item_reminder.provider.component;

import android.content.Context;

import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

public class ItemReminderEventHandler implements ProviderDisposable {

    private ExecutorService mExecutorService;
    private WorkManager mWorkManager;
    private ItemChangeNotifier mItemChangeNotifier;
    private ItemReminderDao mItemReminderDao;
    private CompositeDisposable mCompositeDisposable;

    public ItemReminderEventHandler(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mWorkManager = provider.get(WorkManager.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mCompositeDisposable = new CompositeDisposable();
        init();
    }

    private void init() {
        mCompositeDisposable.add(mItemChangeNotifier.getDeletedItemFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(itemState -> {
                    List<ItemReminder> itemReminderList = mItemReminderDao.findItemReminderByItemId(itemState.getItemId());
                    if (!itemReminderList.isEmpty()) {
                        mItemReminderDao.delete(itemReminderList);
                        for (ItemReminder itemReminder : itemReminderList) {
                            mWorkManager.cancelUniqueWork(itemReminder.taskId);
                        }
                    }
                })
        );
    }

    @Override
    public void dispose(Context context) {
        mCompositeDisposable.dispose();
    }
}
