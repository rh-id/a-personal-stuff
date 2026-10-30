package m.co.rh.id.a_personal_stuff.item_reminder.provider.component;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_personal_stuff.base.backup.ModuleBackupContributor;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleExport;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.notifier.ItemReminderChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_reminder.workmanager.WorkManagerConstants;
import m.co.rh.id.a_personal_stuff.item_reminder.workmanager.worker.ItemReminderNotificationWorker;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the item-reminder module's backup/export/import behavior.
 * Reminders have no image files, and the import reschedules each reminder's
 * WorkManager unique work with a fresh task id (part of the module's import
 * contract, moved here from :app's ImportCmd).
 */
public class ItemReminderBackupContributor implements ModuleBackupContributor {

    private static final String KEY_ITEM_REMINDERS = "itemReminders";

    private final Provider mProvider;
    private final ItemReminderDao mItemReminderDao;
    private final ItemReminderChangeNotifier mItemReminderChangeNotifier;
    private WorkManager mWorkManager;

    public ItemReminderBackupContributor(Provider provider) {
        mProvider = provider;
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mItemReminderChangeNotifier = provider.get(ItemReminderChangeNotifier.class);
    }

    /**
     * Resolved lazily on first import, NOT in the constructor: contributors
     * are constructed inside their module's provides(), i.e. while the
     * provider is still starting. WorkManager.getInstance triggers on-demand
     * initialization, whose Configuration.Provider callback needs the app's
     * provider — still null at that point (crashes startup).
     */
    private WorkManager getWorkManager() {
        if (mWorkManager == null) {
            mWorkManager = mProvider.get(WorkManager.class);
        }
        return mWorkManager;
    }

    @Override
    public List<String> getJsonKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(KEY_ITEM_REMINDERS);
        return keys;
    }

    @Override
    public ModuleExport exportData() {
        List<ItemReminder> itemReminders = mItemReminderDao.findAllItemReminders();
        try {
            return ModuleExport.ofSections(remindersSection(itemReminders));
        } catch (JSONException e) {
            // cannot happen: only primitive values are written
            throw new IllegalStateException("Item reminder backup serialization failed", e);
        }
    }

    @Override
    public void importJson(JSONObject root, Map<Long, Long> oldToNewItemId, File tempDir) throws JSONException {
        List<ItemReminder> itemReminders = remindersFromJson(root.optJSONArray(KEY_ITEM_REMINDERS));
        for (ItemReminder e : itemReminders) {
            Long newItemId = oldToNewItemId.get(e.itemId);
            if (newItemId == null) continue;
            e.id = null;
            e.itemId = newItemId;
            // a fresh unique-work id so re-imported reminders cannot collide
            // with stale WorkManager jobs
            e.taskId = UUID.randomUUID().toString();
            mItemReminderDao.insertItemReminder(e);
            scheduleReminder(e);
            mItemReminderChangeNotifier.added(e);
        }
    }

    // Serialization helpers are public static so tests can assemble fixture
    // backups with the exact production field names.

    public static JSONObject remindersSection(List<ItemReminder> entries) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_ITEM_REMINDERS, remindersToJson(entries));
        return obj;
    }

    public static JSONArray remindersToJson(List<ItemReminder> entries) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ItemReminder e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("id", e.id);
            obj.put("itemId", e.itemId);
            obj.put("taskId", e.taskId);
            obj.put("reminderDateTime", e.reminderDateTime != null ? e.reminderDateTime.getTime() : JSONObject.NULL);
            obj.put("message", e.message);
            obj.put("createdDateTime", e.createdDateTime != null ? e.createdDateTime.getTime() : JSONObject.NULL);
            arr.put(obj);
        }
        return arr;
    }

    public static List<ItemReminder> remindersFromJson(JSONArray arr) throws JSONException {
        List<ItemReminder> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            ItemReminder e = new ItemReminder();
            e.id = obj.optLong("id", 0);
            e.itemId = obj.optLong("itemId", 0);
            e.taskId = obj.optString("taskId", null);
            e.reminderDateTime = obj.isNull("reminderDateTime") ? null : new Date(obj.getLong("reminderDateTime"));
            e.message = obj.optString("message", null);
            e.createdDateTime = obj.isNull("createdDateTime") ? null : new Date(obj.getLong("createdDateTime"));
            list.add(e);
        }
        return list;
    }

    /**
     * Enqueues the reminder's notification unique work; past-due reminders
     * are skipped (they fire no notification after a restore).
     */
    private void scheduleReminder(ItemReminder itemReminder) {
        if (itemReminder.reminderDateTime == null) return;
        long currentMilis = new Date().getTime();
        long reminderMilis = itemReminder.reminderDateTime.getTime();
        long delay = reminderMilis - currentMilis;
        if (delay <= 0) return;
        OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(ItemReminderNotificationWorker.class)
                .setInputData(new Data.Builder()
                        .putLong(WorkManagerConstants.KEY_LONG_ITEM_REMINDER_ID, itemReminder.id)
                        .build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        getWorkManager().enqueueUniqueWork(itemReminder.taskId, ExistingWorkPolicy.KEEP,
                oneTimeWorkRequest);
    }
}
