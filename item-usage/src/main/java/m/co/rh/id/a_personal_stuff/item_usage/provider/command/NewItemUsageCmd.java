package m.co.rh.id.a_personal_stuff.item_usage.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_usage.R;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class NewItemUsageCmd {
    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected ItemUsageChangeNotifier mItemUsageChangeNotifier;
    protected ItemUsageDao mItemUsageDao;

    protected BehaviorSubject<String> mAmountValidSubject;
    protected BehaviorSubject<String> mDescriptionValidSubject;
    protected Subject<String> mAmountValidEmitter;
    protected Subject<String> mDescriptionValidEmitter;

    public NewItemUsageCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemUsageChangeNotifier = provider.get(ItemUsageChangeNotifier.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mAmountValidSubject = BehaviorSubject.create();
        mAmountValidEmitter = mAmountValidSubject.toSerialized();
        mDescriptionValidSubject = BehaviorSubject.create();
        mDescriptionValidEmitter = mDescriptionValidSubject.toSerialized();
    }

    public Single<ItemUsageState> execute(ItemUsageState itemUsageState) {
        return Single.fromCallable(() -> {
                    mItemUsageDao.insertItemUsage(itemUsageState);
                    mItemUsageChangeNotifier.itemUsageAdded(itemUsageState.clone());
                    return itemUsageState;
                }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public boolean valid(ItemUsageState itemUsageState) {
        boolean valid = false;
        if (itemUsageState != null) {
            boolean amtValid;
            boolean descValid;
            ItemUsage itemUsage = itemUsageState.getItemUsage();
            if (itemUsage.amount != 0) {
                amtValid = true;
                mAmountValidEmitter.onNext("");
            } else {
                amtValid = false;
                mAmountValidEmitter.onNext(mAppContext.getString(R.string.amount_cannot_be_0));
            }
            if (itemUsage.description != null && !itemUsage.description.isEmpty()) {
                descValid = true;
                mDescriptionValidEmitter.onNext("");
            } else {
                descValid = false;
                mDescriptionValidEmitter.onNext(mAppContext.getString(R.string.description_is_required));
            }
            valid = amtValid && descValid;
        }
        return valid;
    }

    public String getValidationError() {
        String amtValid = mAmountValidSubject.getValue();
        if (amtValid != null && !amtValid.isEmpty()) {
            return amtValid;
        }
        String descValid = mDescriptionValidSubject.getValue();
        if (descValid != null && !descValid.isEmpty()) {
            return descValid;
        }
        return "";
    }

    public Flowable<String> getAmountValidFlow() {
        return Flowable.fromObservable(mAmountValidEmitter, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getDescriptionValidFlow() {
        return Flowable.fromObservable(mDescriptionValidEmitter, BackpressureStrategy.BUFFER);
    }
}
