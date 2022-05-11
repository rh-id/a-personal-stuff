package m.co.rh.id.a_personal_stuff.app.provider.command;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class DeleteItemImageCmd {
    private ExecutorService mExecutorService;
    private ItemDao mItemDao;
    private ItemChangeNotifier mItemChangeNotifier;

    public DeleteItemImageCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemDao = provider.get(ItemDao.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
    }

    public Single<List<ItemImage>> execute(List<ItemImage> itemImages) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mItemDao.deleteItemImages(itemImages);
            mItemChangeNotifier.itemImageDeleted(itemImages);
            return itemImages;
        }));
    }
}
