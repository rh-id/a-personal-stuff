package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import java.util.Date;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class DeleteItemChecklistItemCmd {
    private ExecutorService mExecutorService;
    private ItemChecklistChangeNotifier mItemChecklistChangeNotifier;
    private ItemChecklistDao mItemChecklistDao;

    public DeleteItemChecklistItemCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChecklistChangeNotifier = provider.get(ItemChecklistChangeNotifier.class);
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
    }

    public Single<ItemChecklistItem> execute(ItemChecklistItem item) {
        return Single.fromCallable(() -> {
            // Bump checklist updatedDateTime
            ItemChecklist checklist = mItemChecklistDao.findItemChecklistStateById(item.itemChecklistId).getItemChecklist();
            if (checklist != null) {
                checklist.updatedDateTime = new Date();
                mItemChecklistDao.updateItemChecklist(checklist);
            }

            // Delete the item
            mItemChecklistDao.deleteItemChecklistItem(item);

            ItemChecklistState state = mItemChecklistDao.findItemChecklistStateById(item.itemChecklistId);
            mItemChecklistChangeNotifier.checklistItemDeleted(item, state.clone());

            return item;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
