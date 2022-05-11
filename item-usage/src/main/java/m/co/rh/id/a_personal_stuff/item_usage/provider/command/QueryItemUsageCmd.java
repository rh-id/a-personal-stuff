package m.co.rh.id.a_personal_stuff.item_usage.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.aprovider.Provider;

public class QueryItemUsageCmd {
    private ExecutorService mExecutorService;
    private ItemUsageDao mItemUsageDao;

    public QueryItemUsageCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
    }

    public Single<ItemUsageState> findItemUsageStateById(long id) {
        return Single.fromFuture(mExecutorService.submit(() ->
                mItemUsageDao.findItemUsageStateById(id)));
    }
}
