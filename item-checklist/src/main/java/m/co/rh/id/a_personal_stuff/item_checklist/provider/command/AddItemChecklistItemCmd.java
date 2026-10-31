package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.component.ItemChecklistService;
import m.co.rh.id.aprovider.Provider;

public class AddItemChecklistItemCmd {
    private ExecutorService mExecutorService;
    private ItemChecklistService mItemChecklistService;

    public AddItemChecklistItemCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChecklistService = provider.get(ItemChecklistService.class);
    }

    public Single<Integer> execute(long checklistId, List<Long> itemIds) {
        // the dedupe + insert + bump-and-notify sequence is shared checklist
        // domain logic (the app-level shopping-list orchestrator appends to
        // the same designated checklist), so it lives in ItemChecklistService
        return Single.fromCallable(() ->
                        mItemChecklistService.addItemIds(checklistId, itemIds))
                .subscribeOn(Schedulers.from(mExecutorService));
    }
}
