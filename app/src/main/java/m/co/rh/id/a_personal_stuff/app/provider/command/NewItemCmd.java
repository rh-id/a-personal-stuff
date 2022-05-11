package m.co.rh.id.a_personal_stuff.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class NewItemCmd {
    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected ItemChangeNotifier mItemChangeNotifier;
    protected ItemDao mItemDao;

    protected BehaviorSubject<String> mNameValidSubject;
    protected BehaviorSubject<String> mAmountValidSubject;

    public NewItemCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
        mItemDao = provider.get(ItemDao.class);
        mNameValidSubject = BehaviorSubject.create();
        mAmountValidSubject = BehaviorSubject.create();
    }

    public boolean valid(ItemState itemState) {
        boolean isValid = false;
        if (itemState != null) {
            boolean nameValid = false;
            boolean amountValid = false;
            boolean expiredDateTimeValid = false;
            String itemName = itemState.getItemName();
            int itemAmount = itemState.getItemAmount();
            if (itemName != null && !itemName.isEmpty()) {
                nameValid = true;
                mNameValidSubject.onNext("");
            } else {
                mNameValidSubject.onNext(mAppContext.getString(R.string.name_is_required));
            }
            if (itemAmount > 0) {
                amountValid = true;
                mAmountValidSubject.onNext("");
            } else {
                mAmountValidSubject.onNext(mAppContext.getString(R.string.amount_must_be_positive));
            }
            isValid = nameValid && amountValid;
        }
        return isValid;
    }

    public Single<ItemState> execute(ItemState itemState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
                    mItemDao.insertItem(itemState);
                    mItemChangeNotifier.itemAdded(itemState.clone());
                    return itemState;
                })
        );
    }

    public String getValidationError() {
        String nameValid = mNameValidSubject.getValue();
        if (nameValid != null && !nameValid.isEmpty()) {
            return nameValid;
        }
        String amountValid = mAmountValidSubject.getValue();
        if (amountValid != null && !amountValid.isEmpty()) {
            return amountValid;
        }
        return "";
    }

    public Flowable<String> getNameValidFlow() {
        return Flowable.fromObservable(mNameValidSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getAmountValidFlow() {
        return Flowable.fromObservable(mAmountValidSubject, BackpressureStrategy.BUFFER);
    }
}
