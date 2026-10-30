package m.co.rh.id.a_personal_stuff.item_reminder.provider.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleDashboardContributor;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.notifier.ItemReminderChangeNotifier;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the item-reminder module's dashboard inputs: reminder changes
 * trigger a refresh; reminders carry no stock so the delta map is empty.
 */
public class ItemReminderDashboardContributor implements ModuleDashboardContributor {

    private final ItemReminderChangeNotifier mItemReminderChangeNotifier;

    public ItemReminderDashboardContributor(Provider provider) {
        mItemReminderChangeNotifier = provider.get(ItemReminderChangeNotifier.class);
    }

    @Override
    public List<Flowable<Object>> getRefreshTriggers() {
        List<Flowable<Object>> triggers = new ArrayList<>();
        triggers.add(mItemReminderChangeNotifier.getAddedFlow()
                .map(itemReminder -> (Object) itemReminder));
        triggers.add(mItemReminderChangeNotifier.getUpdatedFlow()
                .map(itemReminder -> (Object) itemReminder));
        triggers.add(mItemReminderChangeNotifier.getDeletedFlow()
                .map(itemReminder -> (Object) itemReminder));
        return triggers;
    }

    @Override
    public Map<Long, Integer> getStockDeltas() {
        return new HashMap<>();
    }
}
