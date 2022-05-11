package m.co.rh.id.a_personal_stuff.item_maintenance.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
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

    public NewItemMaintenanceCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemMaintenanceChangeNotifier = provider.get(ItemMaintenanceChangeNotifier.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mMaintenanceDateTimeValidSubject = BehaviorSubject.create();
        mDescriptionValidSubject = BehaviorSubject.create();
    }

    public Single<ItemMaintenanceState> execute(ItemMaintenanceState itemMaintenanceState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
                    mItemMaintenanceDao.insertItemMaintenance(itemMaintenanceState);
                    mItemMaintenanceChangeNotifier.itemMaintenanceAdded(itemMaintenanceState.clone());
                    return itemMaintenanceState;
                })
        );
    }

    public boolean valid(ItemMaintenanceState itemMaintenanceState) {
        boolean valid = false;
        if (itemMaintenanceState != null) {
            boolean maintenanceDateTimeValid;
            boolean descValid;
            ItemMaintenance itemMaintenance = itemMaintenanceState.getItemMaintenance();
            if (itemMaintenance.maintenanceDateTime != null) {
                maintenanceDateTimeValid = true;
                mMaintenanceDateTimeValidSubject.onNext("");
            } else {
                maintenanceDateTimeValid = false;
                mMaintenanceDateTimeValidSubject.onNext(mAppContext.getString(R.string.maintenance_date_time_is_required));
            }
            if (itemMaintenance.description != null && !itemMaintenance.description.isEmpty()) {
                descValid = true;
                mDescriptionValidSubject.onNext("");
            } else {
                descValid = false;
                mDescriptionValidSubject.onNext(mAppContext.getString(R.string.description_is_required));
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
        return Flowable.fromObservable(mMaintenanceDateTimeValidSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> getDescriptionValidFlow() {
        return Flowable.fromObservable(mDescriptionValidSubject, BackpressureStrategy.BUFFER);
    }
}
