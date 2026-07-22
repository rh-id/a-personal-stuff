package m.co.rh.id.a_personal_stuff.item_reminder.provider.command;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.aprovider.Provider;

public class UpdateItemReminderCmd extends NewItemReminderCmd {

    public UpdateItemReminderCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<ItemReminder> execute(ItemReminder itemReminder) {
        return Single.fromCallable(() -> {
                    mItemReminderDao.updateItemReminder(itemReminder);
                    ItemReminder clone = itemReminder.clone();
                    // REPLACE atomically cancels the previously scheduled work and
                    // enqueues a fresh request, so an updated reminder date time takes
                    // effect. (cancelUniqueWork + KEEP would race: KEEP can keep the
                    // still-pending old work and discard the new schedule.)
                    OneTimeWorkRequest workRequest = buildWorkRequest(clone);
                    mWorkManager.enqueueUniqueWork(clone.taskId, ExistingWorkPolicy.REPLACE,
                            workRequest);
                    mItemReminderChangeNotifier.updated(clone);
                    return itemReminder;
                }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
