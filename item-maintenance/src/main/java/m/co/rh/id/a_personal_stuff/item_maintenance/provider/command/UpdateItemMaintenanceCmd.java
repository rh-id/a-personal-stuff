package m.co.rh.id.a_personal_stuff.item_maintenance.provider.command;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.aprovider.Provider;

public class UpdateItemMaintenanceCmd extends NewItemMaintenanceCmd {

    public UpdateItemMaintenanceCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<ItemMaintenanceState> execute(ItemMaintenanceState itemMaintenanceState) {
        return Single.fromCallable(() -> {
                    mItemMaintenanceDao.updateItemMaintenance(itemMaintenanceState);
                    mItemMaintenanceChangeNotifier.itemMaintenanceUpdated(itemMaintenanceState.clone());
                    return itemMaintenanceState;
                }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
