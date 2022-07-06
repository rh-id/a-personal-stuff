package m.co.rh.id.a_personal_stuff.base.provider;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.work.WorkManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import co.rh.id.lib.concurrent_utils.concurrent.executor.WeightedThreadPool;
import m.co.rh.id.a_personal_stuff.base.BuildConfig;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.alogger.AndroidLogger;
import m.co.rh.id.alogger.CompositeLogger;
import m.co.rh.id.alogger.FileLogger;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.alogger.ToastLogger;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for base configuration
 */
public class BaseProviderModule implements ProviderModule {
    private static final String TAG = BaseProviderModule.class.getName();


    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = provider.getContext().getApplicationContext();
        providerRegistry.registerModule(new DatabaseProviderModule());
        providerRegistry.register(ExecutorService.class, this::getExecutorService);
        providerRegistry.register(ScheduledExecutorService.class, Executors::newSingleThreadScheduledExecutor);
        providerRegistry.register(Handler.class, () -> new Handler(Looper.getMainLooper()));
        providerRegistry.registerAsync(ILogger.class, () -> {
            ILogger defaultLogger = new AndroidLogger(ILogger.ERROR);
            List<ILogger> loggerList = new ArrayList<>();
            loggerList.add(defaultLogger);
            try {
                int logLevel = ILogger.DEBUG;
                if (BuildConfig.DEBUG) {
                    logLevel = ILogger.VERBOSE;
                }
                ILogger fileLogger = new FileLogger(logLevel,
                        provider.get(FileHelper.class).getLogFile());
                loggerList.add(fileLogger);
            } catch (IOException e) {
                defaultLogger.e(TAG, "Error creating file logger", e);
            }
            try {
                ILogger toastLogger = new ToastLogger(ILogger.INFO, appContext);
                loggerList.add(toastLogger);
            } catch (Throwable throwable) {
                defaultLogger.e(TAG, "Error creating toast logger", throwable);
            }

            return new CompositeLogger(loggerList);
        });

        providerRegistry.registerAsync(WorkManager.class, () -> WorkManager.getInstance(appContext));
        providerRegistry.registerLazy(ItemChangeNotifier.class, ItemChangeNotifier::new);

        providerRegistry.register(NavExtDialogConfig.class, () -> new NavExtDialogConfig(appContext));
        providerRegistry.register(FileHelper.class, () -> new FileHelper(provider));
        providerRegistry.registerAsync(ItemFileHelper.class, () -> new ItemFileHelper(provider));
    }

    private ExecutorService getExecutorService() {
        // thread pool to be used throughout this app lifecycle
        WeightedThreadPool weightedThreadPool = new WeightedThreadPool();
        weightedThreadPool.setMaxWeight(5);
        return weightedThreadPool;
    }

    @Override
    public void dispose(Provider provider) {
        ILogger iLogger = provider.get(ILogger.class);
        ExecutorService executorService = provider.get(ExecutorService.class);
        ScheduledExecutorService scheduledExecutorService = provider.get(ScheduledExecutorService.class);
        try {
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(1500, TimeUnit.MILLISECONDS);
            iLogger.d(TAG, "ExecutorService shutdown? " + terminated);
        } catch (Throwable throwable) {
            iLogger.e(TAG, "Failed to shutdown ExecutorService", throwable);
        }
        try {
            scheduledExecutorService.shutdown();
            boolean terminated = scheduledExecutorService.awaitTermination(1500, TimeUnit.MILLISECONDS);
            iLogger.d(TAG, "ScheduledExecutorService shutdown? " + terminated);
        } catch (Throwable throwable) {
            iLogger.e(TAG, "Failed to shutdown ScheduledExecutorService", throwable);
        }
    }
}
