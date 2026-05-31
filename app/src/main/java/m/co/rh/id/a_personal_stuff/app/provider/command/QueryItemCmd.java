package m.co.rh.id.a_personal_stuff.app.provider.command;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
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

    public Single<List<ItemState>> findItemStateByItemIds(List<Long> itemIds) {
        return Single.fromCallable(() ->
                mItemDao.findItemStatesByIds(itemIds)).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<ItemState> findItemStateByItemId(long itemId) {
        return Single.fromCallable(() ->
                mItemDao.findItemStatesByIds(Collections.singletonList(itemId)).get(0)).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<LinkedHashSet<String>> searchItemTag(String search) {
        return Single.fromCallable(() ->
        {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<ItemTag> itemTags = mItemDao.searchItemTag(search);
            if (!itemTags.isEmpty()) {
                for (ItemTag itemTag : itemTags) {
                    linkedHashSet.add(itemTag.tag);
                }
            }
            return linkedHashSet;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<LinkedHashSet<Item>> searchItemBarcode(String search) {
        return Single.fromCallable(() ->
        {
            LinkedHashSet<Item> linkedHashSet = new LinkedHashSet<>();
            List<Item> itemBarcodes = mItemDao.searchItemBarcode(search);
            if (!itemBarcodes.isEmpty()) {
                LinkedHashSet<String> barCodeHashSet = new LinkedHashSet<>();
                for (Item item : itemBarcodes) {
                    boolean added = barCodeHashSet.add(item.barcode + "\n(" + item.name + ")");
                    if (added) {
                        linkedHashSet.add(item);
                    }
                }
            }
            return linkedHashSet;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
