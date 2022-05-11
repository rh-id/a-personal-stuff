package m.co.rh.id.a_personal_stuff.app.provider.command;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.aprovider.Provider;

public class QueryItemCmd {
    private ExecutorService mExecutorService;
    private ItemDao mItemDao;

    public QueryItemCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemDao = provider.get(ItemDao.class);
    }

    public Single<List<ItemState>> findItemStateByIds(List<Long> itemIds) {
        return Single.fromFuture(mExecutorService.submit(() ->
                mItemDao.findItemStatesByIds(itemIds)));
    }

    public Single<ItemState> findItemStateByItemId(long itemId) {
        return Single.fromFuture(mExecutorService.submit(() ->
                findItemStateByIds(Collections.singletonList(itemId)).blockingGet()
                        .get(0)));
    }

    public Single<LinkedHashSet<String>> searchItemTag(String search) {
        return Single.fromFuture(mExecutorService.submit(() ->
        {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<ItemTag> itemTags = mItemDao.searchItemTag(search);
            if (!itemTags.isEmpty()) {
                for (ItemTag itemTag : itemTags) {
                    linkedHashSet.add(itemTag.tag);
                }
            }
            return linkedHashSet;
        }));
    }
}
