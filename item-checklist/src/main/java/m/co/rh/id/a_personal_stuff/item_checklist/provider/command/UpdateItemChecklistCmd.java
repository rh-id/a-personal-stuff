package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.component.ItemChecklistService;
import m.co.rh.id.aprovider.Provider;

public class UpdateItemChecklistCmd {
    private ExecutorService mExecutorService;
    private ItemChecklistService mItemChecklistService;

    public UpdateItemChecklistCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChecklistService = provider.get(ItemChecklistService.class);
    }

    public Single<ItemChecklistState> execute(ItemChecklistState itemChecklistState) {
        // the timestamp bump + update + checklistUpdated notification is
        // shared checklist domain logic, so it lives in ItemChecklistService
        return Single.fromCallable(() ->
                        mItemChecklistService.updateChecklist(itemChecklistState))
                .subscribeOn(Schedulers.from(mExecutorService));
    }
}
