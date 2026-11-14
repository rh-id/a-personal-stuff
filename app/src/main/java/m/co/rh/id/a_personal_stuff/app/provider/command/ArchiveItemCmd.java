package m.co.rh.id.a_personal_stuff.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.aprovider.Provider;

/**
 * Toggles an item's archived flag IN PLACE (the row stays in the item table)
 * and publishes on the dedicated archived/unarchived flows. It must NEVER
 * publish on {@link ItemChangeNotifier#getDeletedItemFlow()}: the six module
 * event handlers listen only to that flow and would wipe the item's child
 * data on an archive. {@link ItemDao#updateItem(ItemState)} bumps
 * updated_date_time as with any other update.
 */
public class ArchiveItemCmd {
    private ExecutorService mExecutorService;
    private ItemDao mItemDao;
    private ItemChangeNotifier mItemChangeNotifier;

    public ArchiveItemCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemDao = provider.get(ItemDao.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
    }

    public Single<ItemState> archive(ItemState itemState) {
        return Single.fromCallable(() -> {
            // the flag must be set BEFORE updateItem (the write persists the
            // Item instance's fields), but the Item instance is shared with
            // the adapter's row — so a failed write restores the previous
            // value instead of leaving the in-memory state flipped while the
            // DB still disagrees
            itemState.getItem().archived = true;
            try {
                mItemDao.updateItem(itemState);
            } catch (Throwable throwable) {
                itemState.getItem().archived = false;
                throw throwable;
            }
            mItemChangeNotifier.itemArchived(itemState.clone());
            return itemState;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<ItemState> unarchive(ItemState itemState) {
        return Single.fromCallable(() -> {
            itemState.getItem().archived = false;
            try {
                mItemDao.updateItem(itemState);
            } catch (Throwable throwable) {
                itemState.getItem().archived = true;
                throw throwable;
            }
            mItemChangeNotifier.itemUnarchived(itemState.clone());
            return itemState;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
