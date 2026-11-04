package m.co.rh.id.a_personal_stuff.item_lending.provider.component;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresPermission;

import m.co.rh.id.a_personal_stuff.item_lending.R;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLending;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;

public interface ILendingNotificationHandler {
    String CHANNEL_ID_ITEM_LENDING_RETURN = "CHANNEL_ID_ITEM_LENDING_RETURN";
    String GROUP_KEY_ITEM_LENDING_RETURN = "GROUP_KEY_ITEM_LENDING_RETURN";

    @RequiresPermission("android.permission.POST_NOTIFICATIONS")
    void postItemLendingReturnNotification(ItemLending itemLending, ItemState itemState);

    default void createLendingReturnNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.notification_name_item_lending_return);
            String description = context.getString(R.string.notification_description_item_lending_return);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_ITEM_LENDING_RETURN,
                    name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
