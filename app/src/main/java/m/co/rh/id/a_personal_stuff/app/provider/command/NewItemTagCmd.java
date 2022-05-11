package m.co.rh.id.a_personal_stuff.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class NewItemTagCmd {
    private ExecutorService mExecutorService;
    private ItemChangeNotifier mItemChangeNotifier;
    private ItemDao mItemDao;

    public NewItemTagCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
        mItemDao = provider.get(ItemDao.class);
    }

    public Single<ItemTag> execute(ItemTag itemTag) {
        return Single.fromFuture(mExecutorService.submit(() -> {
                    mItemDao.insertTag(itemTag);
                    mItemChangeNotifier.itemTagAdded(itemTag.clone());
                    return itemTag;
                })
        );
    }
}
