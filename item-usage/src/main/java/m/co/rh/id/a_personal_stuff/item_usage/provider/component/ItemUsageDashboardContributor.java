package m.co.rh.id.a_personal_stuff.item_usage.provider.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageTotal;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the item-usage module's dashboard inputs: usage changes trigger
 * a refresh, and the module's signed stock delta is −Σusage per item.
 */
public class ItemUsageDashboardContributor implements ModuleDashboardContributor {

    private final ItemUsageDao mItemUsageDao;
    private final ItemUsageChangeNotifier mItemUsageChangeNotifier;

    public ItemUsageDashboardContributor(Provider provider) {
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mItemUsageChangeNotifier = provider.get(ItemUsageChangeNotifier.class);
    }

    @Override
    public List<Flowable<Object>> getRefreshTriggers() {
        List<Flowable<Object>> triggers = new ArrayList<>();
        triggers.add(mItemUsageChangeNotifier.getAnyItemUsageChangeFlow()
                .map(itemId -> (Object) itemId));
        return triggers;
    }

    @Override
    public Map<Long, Integer> getStockDeltas() {
        // usage reduces the remaining stock: signed delta −Σusage per item
        Map<Long, Integer> deltas = new HashMap<>();
        for (ItemUsageTotal itemUsageTotal : mItemUsageDao.sumAmountGroupByItem()) {
            deltas.put(itemUsageTotal.itemId, -itemUsageTotal.total);
        }
        return deltas;
    }
}
