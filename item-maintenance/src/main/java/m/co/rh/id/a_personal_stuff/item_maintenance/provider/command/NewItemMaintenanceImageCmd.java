package m.co.rh.id.a_personal_stuff.item_maintenance.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.notifier.ItemMaintenanceChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class NewItemMaintenanceImageCmd {
    protected ExecutorService mExecutorService;
    protected ItemMaintenanceChangeNotifier mItemMaintenanceChangeNotifier;
    protected ItemMaintenanceDao mItemMaintenanceDao;

    public NewItemMaintenanceImageCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemMaintenanceChangeNotifier = provider.get(ItemMaintenanceChangeNotifier.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
    }

    public Single<ItemMaintenanceImage> execute(ItemMaintenanceImage itemMaintenanceImage) {
        return Single.fromFuture(mExecutorService.submit(() -> {
                    mItemMaintenanceDao.insertItemMaintenanceImage(itemMaintenanceImage);
                    mItemMaintenanceChangeNotifier.imageAdded(itemMaintenanceImage.clone());
                    return itemMaintenanceImage;
                })
        );
    }
}
