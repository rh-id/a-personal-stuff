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

public class UpdateItemChecklistItemCmd {
    private ExecutorService mExecutorService;
    private ItemChecklistChangeNotifier mItemChecklistChangeNotifier;
    private ItemChecklistDao mItemChecklistDao;

    public UpdateItemChecklistItemCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChecklistChangeNotifier = provider.get(ItemChecklistChangeNotifier.class);
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
    }

    public Single<ItemChecklistItem> execute(ItemChecklistItem item) {
        return Single.fromCallable(() -> {
            // Load checklist to bump its updatedDateTime
            ItemChecklist checklist = mItemChecklistDao.findItemChecklistStateById(item.itemChecklistId).getItemChecklist();
            if (checklist != null) {
                checklist.updatedDateTime = new Date();
                // Use the @Transaction method to update both
                mItemChecklistDao.updateItemChecklistItem(item, checklist);
                ItemChecklistState state = mItemChecklistDao.findItemChecklistStateById(item.itemChecklistId);
                mItemChecklistChangeNotifier.checklistItemUpdated(item, state.clone());
            } else {
                // Fallback if checklist not found
                mItemChecklistDao.updateItemChecklistItem(item);
            }
            return item;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
