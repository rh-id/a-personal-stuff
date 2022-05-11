package m.co.rh.id.a_personal_stuff.item_reminder.provider.command;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_personal_stuff.item_reminder.R;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.notifier.ItemReminderChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_reminder.workmanager.WorkManagerConstants;
import m.co.rh.id.a_personal_stuff.item_reminder.workmanager.worker.ItemReminderNotificationWorker;
import m.co.rh.id.aprovider.Provider;

public class NewItemReminderCmd {
    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected WorkManager mWorkManager;
    protected ItemReminderDao mItemReminderDao;
    protected ItemReminderChangeNotifier mItemReminderChangeNotifier;

    protected BehaviorSubject<String> mReminderDateTimeValidSubject;
    protected BehaviorSubject<String> mMessageValidSubject;

    public NewItemReminderCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mWorkManager = provider.get(WorkManager.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mItemReminderChangeNotifier = provider.get(ItemReminderChangeNotifier.class);
        mReminderDateTimeValidSubject = BehaviorSubject.create();
        mMessageValidSubject = BehaviorSubject.create();
    }

    public Single<ItemReminder> execute(ItemReminder itemReminder) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mItemReminderDao.insertItemReminder(itemReminder);
            ItemReminder clone = itemReminder.clone();
            mExecutorService.execute(() -> {
                setupWork(clone);
                mItemReminderChangeNotifier.added(clone);
            });
            return itemReminder;
        }));
    }

    private void setupWork(ItemReminder itemReminder) {
        long currentMilis = new Date().getTime();
        long reminderMilis = itemReminder.reminderDateTime.getTime();
        OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(ItemReminderNotificationWorker.class)
                .setInputData(new Data.Builder()
                        .putLong(WorkManagerConstants.KEY_LONG_ITEM_REMINDER_ID, itemReminder.id)
                        .build())
                .setInitialDelay(reminderMilis - currentMilis, TimeUnit.MILLISECONDS)
                .build();
        mWorkManager.enqueueUniqueWork(itemReminder.taskId, ExistingWorkPolicy.KEEP,
                oneTimeWorkRequest);
    }

    public boolean valid(ItemReminder itemReminder) {
        boolean valid = false;
        if (itemReminder != null) {
            boolean reminderDateTimeValid;
            boolean messageValid;
            if (itemReminder.reminderDateTime != null) {
                reminderDateTimeValid = true;
                mReminderDateTimeValidSubject.onNext("");
            } else {
                reminderDateTimeValid = false;
                mReminderDateTimeValidSubject.onNext(mAppContext.getString(R.string.reminder_date_time_is_required));
            }
            if (itemReminder.message != null && !itemReminder.message.isEmpty()) {
                messageValid = true;
                mMessageValidSubject.onNext("");
            } else {
                messageValid = false;
                mMessageValidSubject.onNext(mAppContext.getString(R.string.message_is_required));
            }
            valid = reminderDateTimeValid && messageValid;
        }
        return valid;
    }

    public Flowable<String> getRemiderDateTimeValidFlow() {
        return Flowable.fromObservable(mReminderDateTimeValidSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getMessageValidFlow() {
        return Flowable.fromObservable(mMessageValidSubject, BackpressureStrategy.BUFFER);
    }

    public String getValidationError() {
        String reminderDateTimeValid = mReminderDateTimeValidSubject.getValue();
        if (reminderDateTimeValid != null && !reminderDateTimeValid.isEmpty()) {
            return reminderDateTimeValid;
        }
        String messageValid = mMessageValidSubject.getValue();
        if (messageValid != null && !messageValid.isEmpty()) {
            return messageValid;
        }
        return "";
    }
}
