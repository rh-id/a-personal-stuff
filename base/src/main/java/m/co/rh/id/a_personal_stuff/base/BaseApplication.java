package m.co.rh.id.a_personal_stuff.base;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;


public abstract class BaseApplication extends Application implements Configuration.Provider {
    public static BaseApplication of(Context context) {
        if (context.getApplicationContext() instanceof BaseApplication) {
            return (BaseApplication) context.getApplicationContext();
        }
        return null;
    }

    public abstract Provider getProvider();

    public abstract INavigator getNavigator(Activity activity);

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        ExecutorService executorService = getProvider().get(ScheduledExecutorService.class);

        return new Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .setExecutor(executorService)
                .setTaskExecutor(executorService)
                .build();
    }
}
