package m.co.rh.id.a_personal_stuff.item_reminder.provider.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.aprovider.Provider;

public class QueryItemReminderCmd {
    private ExecutorService mExecutorService;
    private ItemReminderDao mItemReminderDao;

    public QueryItemReminderCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
    }

    public Single<LinkedHashSet<String>> searchItemMaintenanceDescription(String search) {
        return Single.fromFuture(mExecutorService.submit(() ->
        {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
            List<ItemReminder> itemReminders = mItemReminderDao.searchItemReminderMessage(search);
            if (!itemReminders.isEmpty()) {
                for (ItemReminder itemReminder : itemReminders) {
                    linkedHashSet.add(itemReminder.message);
                }
            }
            return linkedHashSet;
        }));
    }
}
