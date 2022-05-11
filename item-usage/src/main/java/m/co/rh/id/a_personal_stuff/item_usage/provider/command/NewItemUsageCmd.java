package m.co.rh.id.a_personal_stuff.item_usage.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
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

    public NewItemUsageCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemUsageChangeNotifier = provider.get(ItemUsageChangeNotifier.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mAmountValidSubject = BehaviorSubject.create();
        mDescriptionValidSubject = BehaviorSubject.create();
    }

    public Single<ItemUsageState> execute(ItemUsageState itemUsageState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
                    mItemUsageDao.insertItemUsage(itemUsageState);
                    mItemUsageChangeNotifier.itemUsageAdded(itemUsageState.clone());
                    return itemUsageState;
                })
        );
    }

    public boolean valid(ItemUsageState itemUsageState) {
        boolean valid = false;
        if (itemUsageState != null) {
            boolean amtValid;
            boolean descValid;
            ItemUsage itemUsage = itemUsageState.getItemUsage();
            if (itemUsage.amount != 0) {
                amtValid = true;
                mAmountValidSubject.onNext("");
            } else {
                amtValid = false;
                mAmountValidSubject.onNext(mAppContext.getString(R.string.amount_cannot_be_0));
            }
            if (itemUsage.description != null && !itemUsage.description.isEmpty()) {
                descValid = true;
                mDescriptionValidSubject.onNext("");
            } else {
                descValid = false;
                mDescriptionValidSubject.onNext(mAppContext.getString(R.string.description_is_required));
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
        return Flowable.fromObservable(mAmountValidSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getDescriptionValidFlow() {
        return Flowable.fromObservable(mDescriptionValidSubject, BackpressureStrategy.BUFFER);
    }
}
