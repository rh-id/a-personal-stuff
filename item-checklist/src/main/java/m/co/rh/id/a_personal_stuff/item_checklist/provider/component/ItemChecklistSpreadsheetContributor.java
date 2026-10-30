package m.co.rh.id.a_personal_stuff.item_checklist.provider.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_personal_stuff.base.backup.ModuleSheetRow;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSheetRowSink;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSpreadsheetContributor;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSpreadsheetSheet;
import m.co.rh.id.a_personal_stuff.base.backup.SpreadsheetJoinData;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the checklist module's TWO spreadsheet sheets: "Checklists"
 * (small anchor table, deliberately full-loaded) and "Checklist Items"
 * (keyset-paged, titles resolved from a full load of the anchor table),
 * replacing the hard-coded writers in :app's ExportSpreadsheetCmd.
 */
public class ItemChecklistSpreadsheetContributor implements ModuleSpreadsheetContributor {

    private final ItemChecklistDao mItemChecklistDao;

    public ItemChecklistSpreadsheetContributor(Provider provider) {
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
    }

    @Override
    public List<ModuleSpreadsheetSheet> getSheets() {
        List<ModuleSpreadsheetSheet> sheets = new ArrayList<>();
        sheets.add(new ChecklistsSheet());
        sheets.add(new ChecklistItemsSheet());
        return sheets;
    }

    private class ChecklistsSheet implements ModuleSpreadsheetSheet {

        @Override
        public String getName() {
            return "Checklists";
        }

        @Override
        public List<String> getHeaderKeys() {
            return Arrays.asList(
                    "header_id",
                    "header_title",
                    "form_description",
                    "header_created_date_time",
                    "header_updated_date_time");
        }

        @Override
        public void writeRows(ModuleSheetRowSink rowSink, SpreadsheetJoinData joinData) {
            for (ItemChecklist checklist : mItemChecklistDao.findAllItemChecklists()) {
                ModuleSheetRow row = rowSink.nextRow();
                row.longCell(0, checklist.id);
                row.stringCell(1, checklist.title);
                row.stringCell(2, checklist.description);
                row.dateCell(3, checklist.createdDateTime);
                row.dateCell(4, checklist.updatedDateTime);
            }
        }
    }

    /**
     * Checklist items are keyset-paged like the Usages sheet (see the usage
     * contributor's javadoc); checklist titles resolve from a full load of
     * the small anchor table.
     */
    private class ChecklistItemsSheet implements ModuleSpreadsheetSheet {

        @Override
        public String getName() {
            return "Checklist Items";
        }

        @Override
        public List<String> getHeaderKeys() {
            return Arrays.asList(
                    "header_id",
                    "header_checklist_id",
                    "header_title",
                    "header_item_id",
                    "header_item_name",
                    "header_checked_date_time",
                    "header_created_date_time");
        }

        @Override
        public void writeRows(ModuleSheetRowSink rowSink, SpreadsheetJoinData joinData) {
            Map<Long, String> checklistTitleMap = new HashMap<>();
            for (ItemChecklist checklist : mItemChecklistDao.findAllItemChecklists()) {
                checklistTitleMap.put(checklist.id, checklist.title);
            }
            long lastId = 0L;
            List<ItemChecklistItem> page;
            while (!(page = mItemChecklistDao.findItemChecklistItemsAfter(lastId, PAGE_SIZE)).isEmpty()) {
                for (ItemChecklistItem checklistItem : page) {
                    ModuleSheetRow row = rowSink.nextRow();
                    row.longCell(0, checklistItem.id);
                    row.longCell(1, checklistItem.itemChecklistId);
                    row.stringCell(2, checklistTitleMap.get(checklistItem.itemChecklistId));
                    row.longCell(3, checklistItem.itemId);
                    row.stringCell(4, joinData.itemName(checklistItem.itemId));
                    row.dateCell(5, checklistItem.checkedDateTime);
                    row.dateCell(6, checklistItem.createdDateTime);
                }
                lastId = page.get(page.size() - 1).id;
            }
        }
    }
}
