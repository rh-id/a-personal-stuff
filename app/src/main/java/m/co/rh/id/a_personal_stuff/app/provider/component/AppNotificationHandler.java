package m.co.rh.id.a_personal_stuff.app.provider.component;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import m.co.rh.id.a_personal_stuff.base.util.NotificationPermissionHelper;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.component.IItemReminderNotificationHandler;
import m.co.rh.id.aprovider.Provider;

public class AppNotificationHandler implements IItemReminderNotificationHandler {
    static final String KEY_INT_REQUEST_ID = "KEY_INT_REQUEST_ID";
    public static final String CHANNEL_ID_INVENTORY_ALERT = "CHANNEL_ID_INVENTORY_ALERT";
    public static final String GROUP_KEY_INVENTORY_ALERT = "GROUP_KEY_INVENTORY_ALERT";

    private static final int MAX_DIGEST_NAMES = 5;
    private static final long DIGEST_REF_ID = 0;
    private static final int FIXED_DIGEST_NOTIFICATION_ID = (int) DIGEST_REF_ID;

    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final AndroidNotificationRepo mAndroidNotificationRepo;
    private final ItemDao mItemDao;
    private final ItemReminderDao mItemReminderDao;
    private ReentrantLock mLock;
    private QueueSubject<ItemReminder> mItemReminderSubject;
    private QueueSubject<Long> mInventoryAlertSubject;

    public AppNotificationHandler(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mAndroidNotificationRepo = provider.get(AndroidNotificationRepo.class);
        mItemDao = provider.get(ItemDao.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mLock = new ReentrantLock();
        mItemReminderSubject = new QueueSubject<>();
        mInventoryAlertSubject = new QueueSubject<>();
    }

    public void removeNotification(Intent intent) {
        Integer requestId = getRequestId(intent);
        if (requestId != null) {
            mExecutorService.execute(() ->
            {
                mLock.lock();
                mAndroidNotificationRepo.deleteNotificationByRequestId(requestId);
                mLock.unlock();
            });
        }
    }

    public void processNotification(@NonNull Intent intent) {
        Integer requestId = getRequestId(intent);
        if (requestId != null) {
            mExecutorService.execute(() -> {
                mLock.lock();
                AndroidNotification androidNotification =
                        mAndroidNotificationRepo.findByRequestId(requestId);
                if (androidNotification != null) {
                    if (androidNotification.groupKey.equals(GROUP_KEY_ITEM_REMINDER)) {
                        ItemReminder itemReminder = mItemReminderDao.findItemReminderById(androidNotification.refId);
                        if (itemReminder != null) {
                            mItemReminderSubject.onNext(itemReminder);
                        }
                    } else if (androidNotification.groupKey.equals(GROUP_KEY_INVENTORY_ALERT)) {
                        mInventoryAlertSubject.onNext((long) androidNotification.requestId);
                    }
                    // delete after process notification
                    mAndroidNotificationRepo.deleteNotification(androidNotification);
                }
                mLock.unlock();
            });
        }
    }

    private Integer getRequestId(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getSerializableExtra(KEY_INT_REQUEST_ID, Integer.class);
        }
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            return (Integer) serializable;
        }
        return null;
    }

    @SuppressLint("MissingPermission") // NotificationPermissionHelper.canPostNotifications is the permission check
    @Override
    public void postItemReminderNotification(ItemReminder itemReminder) {
        if (!NotificationPermissionHelper.canPostNotifications(mAppContext)) {
            return;
        }
        mLock.lock();
        Future<Item> itemFuture = mExecutorService.submit(() -> mItemDao.findItemById(itemReminder.itemId));
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
                .setColor(ContextCompat.getColor(mAppContext, m.co.rh.id.a_personal_stuff.base.R.color.daynight_light_green_600_green_600))
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

    public Flowable<Long> getInventoryAlertFlow() {
        return Flowable.fromObservable(mInventoryAlertSubject, BackpressureStrategy.BUFFER);
    }

    @SuppressLint("MissingPermission") // NotificationPermissionHelper.canPostNotifications is the permission check
    public void postInventoryDigestNotification(boolean includeExpiry, boolean includeLowStock,
                                                InventoryStatsCalculator.AlertSummary alertSummary, Date now) {
        if (!NotificationPermissionHelper.canPostNotifications(mAppContext)) {
            return;
        }
        mLock.lock();
        createInventoryAlertNotificationChannel(mAppContext);
        // replace the previous digest so rows/notifications do not accumulate daily
        List<AndroidNotification> priorDigests = mAndroidNotificationRepo
                .findAllByGroupTagAndRefId(GROUP_KEY_INVENTORY_ALERT, DIGEST_REF_ID);
        if (!priorDigests.isEmpty()) {
            mAndroidNotificationRepo.deleteByGroupTagAndRefId(GROUP_KEY_INVENTORY_ALERT, DIGEST_REF_ID);
            NotificationManagerCompat.from(mAppContext).cancel(GROUP_KEY_INVENTORY_ALERT,
                    FIXED_DIGEST_NOTIFICATION_ID);
        }
        List<String> lines = buildDigestLines(includeExpiry, includeLowStock, alertSummary, now);
        if (lines.isEmpty()) {
            mLock.unlock();
            return;
        }
        AndroidNotification androidNotification = new AndroidNotification();
        androidNotification.groupKey = GROUP_KEY_INVENTORY_ALERT;
        androidNotification.refId = DIGEST_REF_ID;
        mAndroidNotificationRepo.insertNotification(androidNotification);
        Intent receiverIntent = new Intent(mAppContext, MainActivity.class);
        receiverIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
        // the fixed request code reuses one PendingIntent across digests,
        // so UPDATE_CURRENT is required to refresh the requestId extra
        int intentFlag = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intentFlag = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(mAppContext, FIXED_DIGEST_NOTIFICATION_ID, receiverIntent,
                intentFlag);
        Intent deleteIntent = new Intent(mAppContext, NotificationDeleteReceiver.class);
        deleteIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
        PendingIntent deletePendingIntent = PendingIntent.getBroadcast(mAppContext, FIXED_DIGEST_NOTIFICATION_ID, deleteIntent,
                intentFlag);
        // distinct affected items: expiringItems includes expired items and
        // may overlap with lowStockItems
        Set<Long> affectedItemIds = new LinkedHashSet<>();
        if (includeExpiry) {
            for (Item item : alertSummary.expiringItems) {
                affectedItemIds.add(item.id);
            }
        }
        if (includeLowStock) {
            for (Item item : alertSummary.lowStockItems) {
                affectedItemIds.add(item.id);
            }
        }
        int affectedCount = affectedItemIds.size();
        String title = mAppContext.getString(R.string.notification_title_inventory_alert_, affectedCount);
        String content = TextUtils.join("\n", lines);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_INVENTORY_ALERT)
                .setSmallIcon(m.co.rh.id.a_personal_stuff.base.R.drawable.ic_notification_launcher)
                .setColorized(true)
                .setColor(ContextCompat.getColor(mAppContext, m.co.rh.id.a_personal_stuff.base.R.color.daynight_light_green_600_green_600))
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setGroup(GROUP_KEY_INVENTORY_ALERT)
                .setAutoCancel(true);
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
        notificationManagerCompat.notify(GROUP_KEY_INVENTORY_ALERT,
                FIXED_DIGEST_NOTIFICATION_ID,
                builder.build());
        mLock.unlock();
    }

    private List<String> buildDigestLines(boolean includeExpiry, boolean includeLowStock,
                                          InventoryStatsCalculator.AlertSummary alertSummary, Date now) {
        List<String> lines = new ArrayList<>();
        if (includeExpiry) {
            List<String> expiredNames = new ArrayList<>();
            List<String> expiringNames = new ArrayList<>();
            for (Item item : alertSummary.expiringItems) {
                if (item.expiredDateTime == null) {
                    continue;
                }
                if (!item.expiredDateTime.after(now)) {
                    if (expiredNames.size() < MAX_DIGEST_NAMES) {
                        expiredNames.add(item.name);
                    }
                } else {
                    if (expiringNames.size() < MAX_DIGEST_NAMES) {
                        expiringNames.add(item.name);
                    }
                }
            }
            if (!expiredNames.isEmpty()) {
                lines.add(mAppContext.getString(R.string.notification_expired_,
                        TextUtils.join(", ", expiredNames)));
            }
            if (!expiringNames.isEmpty()) {
                lines.add(mAppContext.getString(R.string.notification_expiring_soon_,
                        TextUtils.join(", ", expiringNames)));
            }
        }
        if (includeLowStock) {
            List<String> lowStockNames = new ArrayList<>();
            for (Item item : alertSummary.lowStockItems) {
                if (lowStockNames.size() < MAX_DIGEST_NAMES) {
                    lowStockNames.add(mAppContext.getString(R.string.notification_low_stock_item_,
                            item.name, item.minAmount));
                } else {
                    break;
                }
            }
            if (!lowStockNames.isEmpty()) {
                lines.add(mAppContext.getString(R.string.notification_low_stock_,
                        TextUtils.join(", ", lowStockNames)));
            }
        }
        return lines;
    }

    private void createInventoryAlertNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.notification_name_inventory_alert);
            String description = context.getString(R.string.notification_description_inventory_alert);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_INVENTORY_ALERT,
                    name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
