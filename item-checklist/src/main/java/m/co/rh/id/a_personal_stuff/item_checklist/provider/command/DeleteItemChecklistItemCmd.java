package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.component.ItemChecklistService;
import m.co.rh.id.aprovider.Provider;

public class DeleteItemChecklistItemCmd {
    private ExecutorService mExecutorService;
    private ItemChecklistService mItemChecklistService;

    public DeleteItemChecklistItemCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChecklistService = provider.get(ItemChecklistService.class);
    }

    public Single<ItemChecklistItem> execute(ItemChecklistItem item) {
        // the parent bump + delete + re-read + checklistItemDeleted
        // notification is shared checklist domain logic, so it lives in
        // ItemChecklistService
        return Single.fromCallable(() ->
                        mItemChecklistService.deleteChecklistItem(item))
                .subscribeOn(Schedulers.from(mExecutorService));
    }
}
