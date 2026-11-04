package m.co.rh.id.a_personal_stuff.item_lending.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLendingImage;
import m.co.rh.id.a_personal_stuff.item_lending.provider.notifier.ItemLendingChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class NewItemLendingImageCmd {
    private ExecutorService mExecutorService;
    private ItemLendingDao mItemLendingDao;
    private ItemLendingChangeNotifier mItemLendingChangeNotifier;

    public NewItemLendingImageCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemLendingDao = provider.get(ItemLendingDao.class);
        mItemLendingChangeNotifier = provider.get(ItemLendingChangeNotifier.class);
    }

    public Single<ItemLendingImage> execute(ItemLendingImage itemLendingImage) {
        return Single.fromCallable(() -> {
            mItemLendingDao.insertItemLendingImage(itemLendingImage);
            mItemLendingChangeNotifier.imageAdded(itemLendingImage);
            return itemLendingImage;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
