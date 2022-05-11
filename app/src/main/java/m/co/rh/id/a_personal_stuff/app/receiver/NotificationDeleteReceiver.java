package m.co.rh.id.a_personal_stuff.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import m.co.rh.id.a_personal_stuff.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_personal_stuff.base.BaseApplication;
import m.co.rh.id.aprovider.Provider;

public class NotificationDeleteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Provider provider = BaseApplication.of(context).getProvider();
        AppNotificationHandler appNotificationHandler = provider.get(AppNotificationHandler.class);
        appNotificationHandler.removeNotification(intent);
    }
}
