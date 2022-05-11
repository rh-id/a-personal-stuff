package m.co.rh.id.a_personal_stuff.app.provider.command;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.aprovider.Provider;

public class UpdateItemCmd extends NewItemCmd {
    public UpdateItemCmd(Provider provider) {
        super(provider);
    }

    public Single<ItemState> execute(ItemState itemState) {
        return Single.fromFuture(mExecutorService.submit(() -> {
            mItemDao.updateItem(itemState);
            mItemChangeNotifier.itemUpdated(itemState.clone());
            return itemState;
        }));
    }
}
