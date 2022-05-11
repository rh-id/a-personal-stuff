package m.co.rh.id.a_personal_stuff.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class DeleteItemCmd {
    private ExecutorService mExecutorService;
    private ItemDao mItemDao;
    private ItemChangeNotifier mItemChangeNotifier;

    public DeleteItemCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemDao = provider.get(ItemDao.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
    }

    public Single<ItemState> execute(ItemState itemState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mItemDao.deleteItem(itemState);
            mItemChangeNotifier.itemDeleted(itemState.clone());
            return itemState;
        }));
    }
}
