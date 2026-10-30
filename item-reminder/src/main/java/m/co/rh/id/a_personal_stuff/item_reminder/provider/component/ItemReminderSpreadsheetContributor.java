package m.co.rh.id.a_personal_stuff.item_reminder.provider.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import m.co.rh.id.a_personal_stuff.base.backup.ModuleSheetRow;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSheetRowSink;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSpreadsheetContributor;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSpreadsheetSheet;
import m.co.rh.id.a_personal_stuff.base.backup.SpreadsheetJoinData;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the "Reminders" sheet (keyset-paged rows, no image table),
 * replacing the hard-coded writer in :app's ExportSpreadsheetCmd.
 */
public class ItemReminderSpreadsheetContributor implements ModuleSpreadsheetContributor {

    private final ItemReminderDao mItemReminderDao;

    public ItemReminderSpreadsheetContributor(Provider provider) {
        mItemReminderDao = provider.get(ItemReminderDao.class);
    }

    @Override
    public List<ModuleSpreadsheetSheet> getSheets() {
        List<ModuleSpreadsheetSheet> sheets = new ArrayList<>();
        sheets.add(new RemindersSheet());
        return sheets;
    }

    private class RemindersSheet implements ModuleSpreadsheetSheet {

        @Override
        public String getName() {
            return "Reminders";
        }

        @Override
        public List<String> getHeaderKeys() {
            return Arrays.asList(
                    "header_id",
                    "header_item_id",
                    "header_item_name",
                    "header_message",
                    "header_reminder_date_time",
                    "header_created_date_time");
        }

        /**
         * Reminders are keyset-paged like the Usages sheet (see the usage
         * contributor's javadoc); they have no image table.
         */
        @Override
        public void writeRows(ModuleSheetRowSink rowSink, SpreadsheetJoinData joinData) {
            long lastId = 0L;
            List<ItemReminder> page;
            while (!(page = mItemReminderDao.findItemRemindersAfter(lastId, PAGE_SIZE)).isEmpty()) {
                for (ItemReminder reminder : page) {
                    ModuleSheetRow row = rowSink.nextRow();
                    row.longCell(0, reminder.id);
                    row.longCell(1, reminder.itemId);
                    row.stringCell(2, joinData.itemName(reminder.itemId));
                    row.stringCell(3, reminder.message);
                    row.dateCell(4, reminder.reminderDateTime);
                    row.dateCell(5, reminder.createdDateTime);
                }
                lastId = page.get(page.size() - 1).id;
            }
        }
    }
}
