package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class AddItemChecklistItemCmd {
    private ExecutorService mExecutorService;
    private ItemChecklistChangeNotifier mItemChecklistChangeNotifier;
    private ItemChecklistDao mItemChecklistDao;
    private ItemDao mItemDao;

    public AddItemChecklistItemCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChecklistChangeNotifier = provider.get(ItemChecklistChangeNotifier.class);
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
        mItemDao = provider.get(ItemDao.class);
    }

    public Single<Integer> execute(long checklistId, List<Long> itemIds) {
        return Single.fromCallable(() -> {
            // Filter out ids already present
            List<Long> existingItemIds = mItemChecklistDao.findExistingItemIds(checklistId);
            List<Long> newItemIds = itemIds.stream()
                    .filter(id -> !existingItemIds.contains(id))
                    .collect(Collectors.toList());

            if (newItemIds.isEmpty()) {
                return 0;
            }

            // Build ItemChecklistItem rows
            List<ItemChecklistItem> items = new ArrayList<>();
            for (Long itemId : newItemIds) {
                ItemChecklistItem item = new ItemChecklistItem();
                item.itemChecklistId = checklistId;
                item.itemId = itemId;
                item.checkedDateTime = null;
                item.createdDateTime = new Date();
                items.add(item);
            }

            // Insert items
            mItemChecklistDao.insertItemChecklistItems(items);

            // Bump checklist updatedDateTime
            ItemChecklist checklist = mItemChecklistDao.findItemChecklistStateById(checklistId).getItemChecklist();
            if (checklist != null) {
                checklist.updatedDateTime = new Date();
                mItemChecklistDao.updateItemChecklist(checklist);
                ItemChecklistState state = mItemChecklistDao.findItemChecklistStateById(checklistId);
                mItemChecklistChangeNotifier.checklistUpdated(state.clone());
            }

            return newItemIds.size();
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
