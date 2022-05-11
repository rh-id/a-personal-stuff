package m.co.rh.id.a_personal_stuff.item_maintenance.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.notifier.ItemMaintenanceChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class DeleteItemMaintenanceImageCmd {
    private ExecutorService mExecutorService;
    private ItemMaintenanceDao mItemMaintenanceDao;
    private ItemMaintenanceChangeNotifier mItemMaintenanceChangeNotifier;

    public DeleteItemMaintenanceImageCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mItemMaintenanceChangeNotifier = provider.get(ItemMaintenanceChangeNotifier.class);
    }

    public Single<ItemMaintenanceImage> execute(ItemMaintenanceImage itemMaintenanceImage) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mItemMaintenanceDao.delete(itemMaintenanceImage);
            mItemMaintenanceChangeNotifier.imageDeleted(itemMaintenanceImage.clone());
            return itemMaintenanceImage;
        }));
    }
}
