package m.co.rh.id.a_personal_stuff.item_maintenance.provider.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.aprovider.Provider;

public class QueryItemMaintenanceCmd {
    private ExecutorService mExecutorService;
    private ItemMaintenanceDao mItemMaintenanceDao;

    public QueryItemMaintenanceCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
    }

    public Single<ItemMaintenanceState> findItemMaintenanceStateById(long id) {
        return Single.fromFuture(mExecutorService.submit(() ->
                mItemMaintenanceDao.findItemMaintenanceStateById(id)));
    }

    public Single<LinkedHashSet<String>> searchItemMaintenanceDescription(String search) {
        return Single.fromFuture(mExecutorService.submit(() ->
        {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<ItemMaintenance> itemMaintenances = mItemMaintenanceDao.searchItemMaintenanceDescription(search);
            if (!itemMaintenances.isEmpty()) {
                for (ItemMaintenance itemMaintenance : itemMaintenances) {
                    linkedHashSet.add(itemMaintenance.description);
                }
            }
            return linkedHashSet;
        }));
    }
}
