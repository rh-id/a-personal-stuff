package m.co.rh.id.a_personal_stuff.item_lending.provider.command;

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
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_lending.R;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLending;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.a_personal_stuff.item_lending.provider.notifier.ItemLendingChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_lending.workmanager.WorkManagerConstants;
import m.co.rh.id.a_personal_stuff.item_lending.workmanager.worker.ItemLendingReturnNotificationWorker;
import m.co.rh.id.aprovider.Provider;

public class NewItemLendingCmd {
    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected WorkManager mWorkManager;
    protected ItemLendingChangeNotifier mItemLendingChangeNotifier;
    protected ItemLendingDao mItemLendingDao;

    protected BehaviorSubject<String> mBorrowerNameValidSubject;
    protected BehaviorSubject<String> mLendDateTimeValidSubject;
    protected Subject<String> mBorrowerNameValidEmitter;
    protected Subject<String> mLendDateTimeValidEmitter;

    public NewItemLendingCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mWorkManager = provider.get(WorkManager.class);
        mItemLendingChangeNotifier = provider.get(ItemLendingChangeNotifier.class);
        mItemLendingDao = provider.get(ItemLendingDao.class);
        mBorrowerNameValidSubject = BehaviorSubject.create();
        mBorrowerNameValidEmitter = mBorrowerNameValidSubject.toSerialized();
        mLendDateTimeValidSubject = BehaviorSubject.create();
        mLendDateTimeValidEmitter = mLendDateTimeValidSubject.toSerialized();
    }

    public Single<ItemLendingState> execute(ItemLendingState itemLendingState) {
        return Single.fromCallable(() -> {
                    mItemLendingDao.insertItemLending(itemLendingState);
                    ItemLendingState clone = itemLendingState.clone();
                    setupWork(clone.getItemLending());
                    mItemLendingChangeNotifier.itemLendingAdded(clone);
                    return itemLendingState;
                }).subscribeOn(Schedulers.from(mExecutorService));
    }

    protected void setupWork(ItemLending itemLending) {
        // only an unreturned lending with a future due date needs a return
        // reminder; anything else must not schedule (or keep) work
        if (!isReturnWorkNeeded(itemLending)) return;
        mWorkManager.enqueueUniqueWork(itemLending.taskId, ExistingWorkPolicy.KEEP,
                buildWorkRequest(itemLending));
    }

    /**
     * Builds the notification work request for the given lending. Extracted so
     * the update command can re-enqueue with {@link ExistingWorkPolicy#REPLACE}.
     */
    protected OneTimeWorkRequest buildWorkRequest(ItemLending itemLending) {
        long currentMilis = new Date().getTime();
        long dueMilis = itemLending.dueDateTime.getTime();
        return new OneTimeWorkRequest.Builder(ItemLendingReturnNotificationWorker.class)
                .setInputData(new Data.Builder()
                        .putLong(WorkManagerConstants.KEY_LONG_ITEM_LENDING_ID, itemLending.id)
                        .build())
                .setInitialDelay(dueMilis - currentMilis, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * A return reminder is only needed while the lending is open and its due
     * date lies in the future.
     */
    protected boolean isReturnWorkNeeded(ItemLending itemLending) {
        if (itemLending.dueDateTime == null || itemLending.returnedDateTime != null) {
            return false;
        }
        long currentMilis = new Date().getTime();
        long dueMilis = itemLending.dueDateTime.getTime();
        return dueMilis > currentMilis;
    }

    public boolean valid(ItemLendingState itemLendingState) {
        boolean valid = false;
        if (itemLendingState != null) {
            boolean borrowerNameValid;
            boolean lendDateTimeValid;
            ItemLending itemLending = itemLendingState.getItemLending();
            if (itemLending.borrowerName != null && !itemLending.borrowerName.isEmpty()) {
                borrowerNameValid = true;
                mBorrowerNameValidEmitter.onNext("");
            } else {
                borrowerNameValid = false;
                mBorrowerNameValidEmitter.onNext(mAppContext.getString(R.string.borrower_name_is_required));
            }
            if (itemLending.lendDateTime != null) {
                lendDateTimeValid = true;
                mLendDateTimeValidEmitter.onNext("");
            } else {
                lendDateTimeValid = false;
                mLendDateTimeValidEmitter.onNext(mAppContext.getString(R.string.lend_date_time_is_required));
            }
            valid = borrowerNameValid && lendDateTimeValid;
        }
        return valid;
    }

    public String getValidationError() {
        String borrowerNameValid = mBorrowerNameValidSubject.getValue();
        if (borrowerNameValid != null && !borrowerNameValid.isEmpty()) {
            return borrowerNameValid;
        }
        String lendDateTimeValid = mLendDateTimeValidSubject.getValue();
        if (lendDateTimeValid != null && !lendDateTimeValid.isEmpty()) {
            return lendDateTimeValid;
        }
        return "";
    }

    public Flowable<String> getBorrowerNameValidFlow() {
        return Flowable.fromObservable(mBorrowerNameValidEmitter, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getLendDateTimeValidFlow() {
        return Flowable.fromObservable(mLendDateTimeValidEmitter, BackpressureStrategy.BUFFER);
    }
}
