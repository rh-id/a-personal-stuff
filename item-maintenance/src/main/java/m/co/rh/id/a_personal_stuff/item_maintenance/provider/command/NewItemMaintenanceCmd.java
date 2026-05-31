package m.co.rh.id.a_personal_stuff.item_maintenance.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_maintenance.R;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.notifier.ItemMaintenanceChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class NewItemMaintenanceCmd {
    protected Context mAppContext;
    protected ExecutorService mExecutorService;
    protected ItemMaintenanceChangeNotifier mItemMaintenanceChangeNotifier;
    protected ItemMaintenanceDao mItemMaintenanceDao;

    protected BehaviorSubject<String> mMaintenanceDateTimeValidSubject;
    protected BehaviorSubject<String> mDescriptionValidSubject;
    protected Subject<String> mMaintenanceDateTimeValidEmitter;
    protected Subject<String> mDescriptionValidEmitter;

    public NewItemMaintenanceCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemMaintenanceChangeNotifier = provider.get(ItemMaintenanceChangeNotifier.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mMaintenanceDateTimeValidSubject = BehaviorSubject.create();
        mMaintenanceDateTimeValidEmitter = mMaintenanceDateTimeValidSubject.toSerialized();
        mDescriptionValidSubject = BehaviorSubject.create();
        mDescriptionValidEmitter = mDescriptionValidSubject.toSerialized();
    }

    public Single<ItemMaintenanceState> execute(ItemMaintenanceState itemMaintenanceState) {
        return Single.fromCallable(() -> {
                    mItemMaintenanceDao.insertItemMaintenance(itemMaintenanceState);
                    mItemMaintenanceChangeNotifier.itemMaintenanceAdded(itemMaintenanceState.clone());
                    return itemMaintenanceState;
                }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public boolean valid(ItemMaintenanceState itemMaintenanceState) {
        boolean valid = false;
        if (itemMaintenanceState != null) {
            boolean maintenanceDateTimeValid;
            boolean descValid;
            ItemMaintenance itemMaintenance = itemMaintenanceState.getItemMaintenance();
            if (itemMaintenance.maintenanceDateTime != null) {
                maintenanceDateTimeValid = true;
                mMaintenanceDateTimeValidEmitter.onNext("");
            } else {
                maintenanceDateTimeValid = false;
                mMaintenanceDateTimeValidEmitter.onNext(mAppContext.getString(R.string.maintenance_date_time_is_required));
            }
            if (itemMaintenance.description != null && !itemMaintenance.description.isEmpty()) {
                descValid = true;
                mDescriptionValidEmitter.onNext("");
            } else {
                descValid = false;
                mDescriptionValidEmitter.onNext(mAppContext.getString(R.string.description_is_required));
            }
            valid = maintenanceDateTimeValid && descValid;
        }
        return valid;
    }

    public String getValidationError() {
        String maintenanceDateTimeValid = mMaintenanceDateTimeValidSubject.getValue();
        if (maintenanceDateTimeValid != null && !maintenanceDateTimeValid.isEmpty()) {
            return maintenanceDateTimeValid;
        }
        String descValid = mDescriptionValidSubject.getValue();
        if (descValid != null && !descValid.isEmpty()) {
            return descValid;
        }
        return "";
    }

    public Flowable<String> getMaintenanceDateTimeValidFlow() {
        return Flowable.fromObservable(mMaintenanceDateTimeValidEmitter, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getDescriptionValidFlow() {
        return Flowable.fromObservable(mDescriptionValidEmitter, BackpressureStrategy.BUFFER);
    }
}
