package m.co.rh.id.a_personal_stuff.app.provider.component;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import co.rh.id.lib.rx3_utils.subject.QueueSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.MainActivity;
import m.co.rh.id.a_personal_stuff.app.receiver.NotificationDeleteReceiver;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.AndroidNotification;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.repository.AndroidNotificationRepo;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.component.IItemReminderNotificationHandler;
import m.co.rh.id.aprovider.Provider;

public class AppNotificationHandler implements IItemReminderNotificationHandler {
    static final String KEY_INT_REQUEST_ID = "KEY_INT_REQUEST_ID";

    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final AndroidNotificationRepo mAndroidNotificationRepo;
    private final ItemDao mItemDao;
    private final ItemReminderDao mItemReminderDao;
    private ReentrantLock mLock;
    private QueueSubject<ItemReminder> mItemReminderSubject;

    public AppNotificationHandler(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mAndroidNotificationRepo = provider.get(AndroidNotificationRepo.class);
        mItemDao = provider.get(ItemDao.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mLock = new ReentrantLock();
        mItemReminderSubject = new QueueSubject<>();
    }

    public void removeNotification(Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.execute(() ->
            {
                mLock.lock();
                mAndroidNotificationRepo.deleteNotificationByRequestId((int) serializable);
                mLock.unlock();
            });
        }
    }

    public void processNotification(@NonNull Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.execute(() -> {
                mLock.lock();
                AndroidNotification androidNotification =
                        mAndroidNotificationRepo.findByRequestId((int) serializable);
                if (androidNotification != null) {
                    if (androidNotification.groupKey.equals(GROUP_KEY_ITEM_REMINDER)) {
                        ItemReminder itemReminder = mItemReminderDao.findItemReminderById(androidNotification.refId);
                        if (itemReminder != null) {
                            mItemReminderSubject.onNext(itemReminder);
                        }
                    }
                    // delete after process notification
                    mAndroidNotificationRepo.deleteNotification(androidNotification);
                }
                mLock.unlock();
            });
        }
    }

    @Override
    public void postItemReminderNotification(ItemReminder itemReminder) {
        mLock.lock();
        Future<Item> itemFuture = mExecutorService.submit(() -> mItemDao.finditemById(itemReminder.itemId));
        createItemReminderNotificationChannel(mAppContext);
        AndroidNotification androidNotification = new AndroidNotification();
        androidNotification.groupKey = GROUP_KEY_ITEM_REMINDER;
        androidNotification.refId = itemReminder.id;
        mAndroidNotificationRepo.insertNotification(androidNotification);
        Intent receiverIntent = new Intent(mAppContext, MainActivity.class);
        receiverIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
        int intentFlag = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intentFlag = PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(mAppContext, androidNotification.requestId, receiverIntent,
                intentFlag);
        Intent deleteIntent = new Intent(mAppContext, NotificationDeleteReceiver.class);
        deleteIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
        PendingIntent deletePendingIntent = PendingIntent.getBroadcast(mAppContext, androidNotification.requestId, deleteIntent,
                intentFlag);
        Item item;
        try {
            item = itemFuture.get();
        } catch (Throwable t) {
            mAndroidNotificationRepo.deleteNotification(androidNotification);
            mLock.unlock();
            return;
        }

        String title = mAppContext.getString(R.string.notification_title_item_reminder_, item.name);
        String content = itemReminder.message;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_ITEM_REMINDER)
                .setSmallIcon(m.co.rh.id.a_personal_stuff.base.R.drawable.ic_notification_launcher)
                .setColorized(true)
                .setColor(mAppContext.getResources().getColor(m.co.rh.id.a_personal_stuff.base.R.color.light_green_600))
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setGroup(GROUP_KEY_ITEM_REMINDER)
                .setAutoCancel(true);
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
        notificationManagerCompat.notify(GROUP_KEY_ITEM_REMINDER,
                androidNotification.requestId,
                builder.build());
        mLock.unlock();
    }

    public Flowable<ItemReminder> getItemReminderFlow() {
        return Flowable.fromObservable(mItemReminderSubject, BackpressureStrategy.BUFFER);
    }
}
