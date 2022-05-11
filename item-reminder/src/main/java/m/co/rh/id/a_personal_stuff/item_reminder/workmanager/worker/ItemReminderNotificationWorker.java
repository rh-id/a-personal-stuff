package m.co.rh.id.a_personal_stuff.item_reminder.workmanager.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import m.co.rh.id.a_personal_stuff.base.BaseApplication;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.component.IItemReminderNotificationHandler;
import m.co.rh.id.a_personal_stuff.item_reminder.workmanager.WorkManagerConstants;
import m.co.rh.id.aprovider.Provider;

public class ItemReminderNotificationWorker extends Worker {

    public ItemReminderNotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        long itemReminderId = getInputData().getLong(WorkManagerConstants.KEY_LONG_ITEM_REMINDER_ID, -1);
        Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
        ItemReminderDao itemReminderDao = provider.get(ItemReminderDao.class);
        IItemReminderNotificationHandler notificationHandler = provider.get(IItemReminderNotificationHandler.class);
        ItemReminder itemReminder = itemReminderDao.findItemReminderById(itemReminderId);
        if (itemReminder != null) {
            notificationHandler.postItemReminderNotification(itemReminder);
        }
        return Result.success();
    }
}
