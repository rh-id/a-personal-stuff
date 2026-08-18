package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import java.util.Date;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.aprovider.Provider;

public class UpdateItemChecklistCmd {
    private ExecutorService mExecutorService;
    private ItemChecklistChangeNotifier mItemChecklistChangeNotifier;
    private ItemChecklistDao mItemChecklistDao;

    public UpdateItemChecklistCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemChecklistChangeNotifier = provider.get(ItemChecklistChangeNotifier.class);
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
    }

    public Single<ItemChecklistState> execute(ItemChecklistState itemChecklistState) {
        return Single.fromCallable(() -> {
                    itemChecklistState.setUpdatedDateTime(new Date());
                    mItemChecklistDao.updateItemChecklist(itemChecklistState);
                    mItemChecklistChangeNotifier.checklistUpdated(itemChecklistState.clone());
                    return itemChecklistState;
                }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
