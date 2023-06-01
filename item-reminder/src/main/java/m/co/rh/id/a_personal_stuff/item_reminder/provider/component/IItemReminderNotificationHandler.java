package m.co.rh.id.a_personal_stuff.item_reminder.provider.component;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresPermission;

import m.co.rh.id.a_personal_stuff.item_reminder.R;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;

public interface IItemReminderNotificationHandler {
    String CHANNEL_ID_ITEM_REMINDER = "CHANNEL_ID_ITEM_REMINDER";
    String GROUP_KEY_ITEM_REMINDER = "GROUP_KEY_ITEM_REMINDER";

    @RequiresPermission("android.permission.POST_NOTIFICATIONS")
    void postItemReminderNotification(ItemReminder itemReminder);

    default void createItemReminderNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.notification_name_item_reminder);
            String description = context.getString(R.string.notification_description_item_reminder);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_ITEM_REMINDER,
                    name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
