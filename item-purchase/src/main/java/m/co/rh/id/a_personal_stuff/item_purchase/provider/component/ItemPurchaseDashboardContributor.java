package m.co.rh.id.a_personal_stuff.item_purchase.provider.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseTotal;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the item-purchase module's dashboard inputs: purchase changes
 * trigger a refresh, and the module's signed stock delta is +Σpurchase per
 * item.
 */
public class ItemPurchaseDashboardContributor implements ModuleDashboardContributor {

    private final ItemPurchaseDao mItemPurchaseDao;
    private final ItemPurchaseChangeNotifier mItemPurchaseChangeNotifier;

    public ItemPurchaseDashboardContributor(Provider provider) {
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
        mItemPurchaseChangeNotifier = provider.get(ItemPurchaseChangeNotifier.class);
    }

    @Override
    public List<Flowable<Object>> getRefreshTriggers() {
        List<Flowable<Object>> triggers = new ArrayList<>();
        triggers.add(mItemPurchaseChangeNotifier.getAnyItemPurchaseChangeFlow()
                .map(itemId -> (Object) itemId));
        return triggers;
    }

    @Override
    public Map<Long, Integer> getStockDeltas() {
        // purchases increase the remaining stock: signed delta +Σpurchase per item
        Map<Long, Integer> deltas = new HashMap<>();
        for (ItemPurchaseTotal itemPurchaseTotal : mItemPurchaseDao.sumAmountGroupByItem()) {
            deltas.put(itemPurchaseTotal.itemId, itemPurchaseTotal.total);
        }
        return deltas;
    }
}
