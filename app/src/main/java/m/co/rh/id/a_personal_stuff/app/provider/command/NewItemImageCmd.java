package m.co.rh.id.a_personal_stuff.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class NewItemImageCmd {
    private ExecutorService mExecutorService;
    private ItemDao mItemDao;
    private ItemChangeNotifier mItemChangeNotifier;

    public NewItemImageCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemDao = provider.get(ItemDao.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
    }

    public Single<ItemImage> execute(ItemImage itemImage) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mItemDao.insertItemImage(itemImage);
            mItemChangeNotifier.itemImageAdded(itemImage);
            return itemImage;
        }));
    }
}
