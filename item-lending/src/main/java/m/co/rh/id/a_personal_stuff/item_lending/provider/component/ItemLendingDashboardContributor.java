package m.co.rh.id.a_personal_stuff.item_lending.provider.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.base.contributor.ModuleDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_lending.provider.notifier.ItemLendingChangeNotifier;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the item-lending module's dashboard inputs: lending changes
 * trigger a refresh; lendings carry no stock so the delta map is empty.
 */
public class ItemLendingDashboardContributor implements ModuleDashboardContributor {

    private final ItemLendingChangeNotifier mItemLendingChangeNotifier;

    public ItemLendingDashboardContributor(Provider provider) {
        mItemLendingChangeNotifier = provider.get(ItemLendingChangeNotifier.class);
    }

    @Override
    public List<Flowable<Object>> getRefreshTriggers() {
        List<Flowable<Object>> triggers = new ArrayList<>();
        triggers.add(mItemLendingChangeNotifier.getAddedItemLendingFlow()
                .map(itemLendingState -> (Object) itemLendingState));
        triggers.add(mItemLendingChangeNotifier.getUpdatedItemLendingFlow()
                .map(itemLendingState -> (Object) itemLendingState));
        triggers.add(mItemLendingChangeNotifier.getDeletedItemLendingFlow()
                .map(itemLendingState -> (Object) itemLendingState));
        return triggers;
    }

    @Override
    public Map<Long, Integer> getStockDeltas() {
        // lending does not affect the stock: borrow/return is custody, not a
        // quantity change, so the delta map stays empty
        return new HashMap<>();
    }
}
