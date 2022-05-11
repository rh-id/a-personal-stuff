package m.co.rh.id.a_personal_stuff.item_maintenance.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.notifier.ItemMaintenanceChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class DeleteItemMaintenanceCmd {
    private ExecutorService mExecutorService;
    private ItemMaintenanceDao mItemMaintenanceDao;
    private ItemMaintenanceChangeNotifier mItemMaintenanceChangeNotifier;

    public DeleteItemMaintenanceCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mItemMaintenanceChangeNotifier = provider.get(ItemMaintenanceChangeNotifier.class);
    }

    public Single<ItemMaintenanceState> execute(ItemMaintenanceState itemMaintenanceState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mItemMaintenanceDao.deleteItemMaintenanceState(itemMaintenanceState);
            mItemMaintenanceChangeNotifier.itemMaintenanceDeleted(itemMaintenanceState.clone());
            return itemMaintenanceState;
        }));
    }
}
