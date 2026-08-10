package m.co.rh.id.a_personal_stuff.item_purchase.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_purchase.R;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class NewItemPurchaseCmd {
    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected ItemPurchaseChangeNotifier mItemPurchaseChangeNotifier;
    protected ItemPurchaseDao mItemPurchaseDao;

    protected BehaviorSubject<String> mAmountValidSubject;
    protected BehaviorSubject<String> mDescriptionValidSubject;
    protected BehaviorSubject<String> mPurchaseDateTimeValidSubject;
    protected Subject<String> mAmountValidEmitter;
    protected Subject<String> mDescriptionValidEmitter;
    protected Subject<String> mPurchaseDateTimeValidEmitter;

    public NewItemPurchaseCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemPurchaseChangeNotifier = provider.get(ItemPurchaseChangeNotifier.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
        mAmountValidSubject = BehaviorSubject.create();
        mAmountValidEmitter = mAmountValidSubject.toSerialized();
        mDescriptionValidSubject = BehaviorSubject.create();
        mDescriptionValidEmitter = mDescriptionValidSubject.toSerialized();
        mPurchaseDateTimeValidSubject = BehaviorSubject.create();
        mPurchaseDateTimeValidEmitter = mPurchaseDateTimeValidSubject.toSerialized();
    }

    public Single<ItemPurchaseState> execute(ItemPurchaseState itemPurchaseState) {
        return Single.fromCallable(() -> {
                    mItemPurchaseDao.insertItemPurchase(itemPurchaseState);
                    mItemPurchaseChangeNotifier.itemPurchaseAdded(itemPurchaseState.clone());
                    return itemPurchaseState;
                }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public boolean valid(ItemPurchaseState itemPurchaseState) {
        boolean valid = false;
        if (itemPurchaseState != null) {
            boolean amtValid;
            boolean descValid;
            boolean dateTimeValid;
            ItemPurchase itemPurchase = itemPurchaseState.getItemPurchase();
            if (itemPurchase.amount > 0) {
                amtValid = true;
                mAmountValidEmitter.onNext("");
            } else {
                amtValid = false;
                mAmountValidEmitter.onNext(mAppContext.getString(R.string.amount_must_be_positive));
            }
            if (itemPurchase.description != null && !itemPurchase.description.isEmpty()) {
                descValid = true;
                mDescriptionValidEmitter.onNext("");
            } else {
                descValid = false;
                mDescriptionValidEmitter.onNext(mAppContext.getString(R.string.description_is_required));
            }
            if (itemPurchase.purchaseDateTime != null) {
                dateTimeValid = true;
                mPurchaseDateTimeValidEmitter.onNext("");
            } else {
                dateTimeValid = false;
                mPurchaseDateTimeValidEmitter.onNext(mAppContext.getString(R.string.purchase_date_time_is_required));
            }
            valid = amtValid && descValid && dateTimeValid;
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
        String dateTimeValid = mPurchaseDateTimeValidSubject.getValue();
        if (dateTimeValid != null && !dateTimeValid.isEmpty()) {
            return dateTimeValid;
        }
        return "";
    }

    public Flowable<String> getAmountValidFlow() {
        return Flowable.fromObservable(mAmountValidEmitter, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getDescriptionValidFlow() {
        return Flowable.fromObservable(mDescriptionValidEmitter, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getPurchaseDateTimeValidFlow() {
        return Flowable.fromObservable(mPurchaseDateTimeValidEmitter, BackpressureStrategy.BUFFER);
    }
}
