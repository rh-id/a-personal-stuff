package m.co.rh.id.a_personal_stuff.item_reminder.provider.command;

import androidx.work.WorkManager;

import java.util.Collections;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.notifier.ItemReminderChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class DeleteItemReminderCmd {
    protected ExecutorService mExecutorService;
    protected WorkManager mWorkManager;
    protected ItemReminderDao mItemReminderDao;
    protected ItemReminderChangeNotifier mItemReminderChangeNotifier;

    public DeleteItemReminderCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mWorkManager = provider.get(WorkManager.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mItemReminderChangeNotifier = provider.get(ItemReminderChangeNotifier.class);
    }

    public Single<ItemReminder> execute(ItemReminder itemReminder) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mItemReminderDao.delete(Collections.singletonList(itemReminder));
            mWorkManager.cancelUniqueWork(itemReminder.taskId);
            mItemReminderChangeNotifier.deleted(itemReminder.clone());
            return itemReminder;
        }));
    }
}
