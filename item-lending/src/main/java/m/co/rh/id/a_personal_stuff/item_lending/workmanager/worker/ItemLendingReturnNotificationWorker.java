package m.co.rh.id.a_personal_stuff.item_lending.workmanager.worker;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Collections;
import java.util.List;

import m.co.rh.id.a_personal_stuff.base.BaseApplication;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.util.NotificationPermissionHelper;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.a_personal_stuff.item_lending.provider.component.ILendingNotificationHandler;
import m.co.rh.id.a_personal_stuff.item_lending.workmanager.WorkManagerConstants;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class ItemLendingReturnNotificationWorker extends Worker {

    private static final String TAG = ItemLendingReturnNotificationWorker.class.getName();

    public ItemLendingReturnNotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @SuppressLint("MissingPermission") // NotificationPermissionHelper.canPostNotifications is the permission check
    @Override
    public Result doWork() {
        try {
            long itemLendingId = getInputData().getLong(WorkManagerConstants.KEY_LONG_ITEM_LENDING_ID, -1);
            Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
            ItemLendingDao itemLendingDao = provider.get(ItemLendingDao.class);
            ILendingNotificationHandler notificationHandler = provider.get(ILendingNotificationHandler.class);
            ItemLendingState itemLendingState = itemLendingDao.findItemLendingStateById(itemLendingId);
            // no-op when the lending row is gone (e.g. swept with its item) or
            // was returned in the meantime: the reminder is stale either way
            if (itemLendingState != null
                    && itemLendingState.getReturnedDateTime() == null) {
                ItemDao itemDao = provider.get(ItemDao.class);
                List<ItemState> itemStates = itemDao.findItemStatesByIds(Collections.singletonList(
                        itemLendingState.getItemId()));
                if (!itemStates.isEmpty()
                        && NotificationPermissionHelper.canPostNotifications(getApplicationContext())) {
                    notificationHandler.postItemLendingReturnNotification(
                            itemLendingState.getItemLending(), itemStates.get(0));
                }
            }
            return Result.success();
        } catch (Exception e) {
            try {
                BaseApplication.of(getApplicationContext()).getProvider()
                        .get(ILogger.class).e(TAG, e.getMessage(), e);
            } catch (Throwable ignored) {
            }
            return Result.failure();
        }
    }
}
