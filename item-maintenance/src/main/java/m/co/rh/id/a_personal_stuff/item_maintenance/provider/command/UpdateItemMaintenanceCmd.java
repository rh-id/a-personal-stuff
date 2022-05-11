package m.co.rh.id.a_personal_stuff.item_maintenance.provider.command;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.aprovider.Provider;

public class UpdateItemMaintenanceCmd extends NewItemMaintenanceCmd {

    public UpdateItemMaintenanceCmd(Provider provider) {
        super(provider);
    }

    @Override
    public Single<ItemMaintenanceState> execute(ItemMaintenanceState itemMaintenanceState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
                    mItemMaintenanceDao.updateItemMaintenance(itemMaintenanceState);
                    mItemMaintenanceChangeNotifier.itemMaintenanceUpdated(itemMaintenanceState.clone());
                    return itemMaintenanceState;
                })
        );
    }
}
