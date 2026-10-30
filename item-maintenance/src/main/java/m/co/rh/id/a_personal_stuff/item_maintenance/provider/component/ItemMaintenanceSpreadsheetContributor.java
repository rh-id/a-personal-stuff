package m.co.rh.id.a_personal_stuff.item_maintenance.provider.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import m.co.rh.id.a_personal_stuff.base.backup.ModuleSheetRow;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSheetRowSink;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSpreadsheetContributor;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSpreadsheetSheet;
import m.co.rh.id.a_personal_stuff.base.backup.SpreadsheetJoinData;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the "Maintenances" sheet (keyset-paged rows with the per-page
 * image join), replacing the hard-coded writer in :app's
 * ExportSpreadsheetCmd.
 */
public class ItemMaintenanceSpreadsheetContributor implements ModuleSpreadsheetContributor {

    private final ItemMaintenanceDao mItemMaintenanceDao;

    public ItemMaintenanceSpreadsheetContributor(Provider provider) {
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
    }

    @Override
    public List<ModuleSpreadsheetSheet> getSheets() {
        List<ModuleSpreadsheetSheet> sheets = new ArrayList<>();
        sheets.add(new MaintenancesSheet());
        return sheets;
    }

    private class MaintenancesSheet implements ModuleSpreadsheetSheet {

        @Override
        public String getName() {
            return "Maintenances";
        }

        @Override
        public List<String> getHeaderKeys() {
            return Arrays.asList(
                    "header_id",
                    "header_item_id",
                    "header_item_name",
                    "form_description",
                    "header_cost",
                    "header_maintenance_date_time",
                    "header_created_date_time",
                    "header_images");
        }

        /**
         * Maintenances are keyset-paged like the Usages sheet (see the usage
         * contributor's javadoc).
         */
        @Override
        public void writeRows(ModuleSheetRowSink rowSink, SpreadsheetJoinData joinData) {
            long lastId = 0L;
            List<ItemMaintenance> page;
            while (!(page = mItemMaintenanceDao.findItemMaintenancesAfter(lastId, PAGE_SIZE)).isEmpty()) {
                List<Long> pageIds = page.stream().map(maintenance -> maintenance.id)
                        .collect(Collectors.toList());
                Map<Long, String> imagesByMaintenanceId = new HashMap<>();
                for (ItemMaintenanceImage image : mItemMaintenanceDao.findItemMaintenanceImagesByItemMaintenanceIds(pageIds)) {
                    if (image.itemMaintenanceId == null || image.fileName == null || image.fileName.isEmpty()) {
                        continue;
                    }
                    String joined = imagesByMaintenanceId.get(image.itemMaintenanceId);
                    imagesByMaintenanceId.put(image.itemMaintenanceId,
                            joined == null || joined.isEmpty()
                                    ? image.fileName
                                    : joined + ", " + image.fileName);
                }
                for (ItemMaintenance maintenance : page) {
                    ModuleSheetRow row = rowSink.nextRow();
                    row.longCell(0, maintenance.id);
                    row.longCell(1, maintenance.itemId);
                    row.stringCell(2, joinData.itemName(maintenance.itemId));
                    row.stringCell(3, maintenance.description);
                    row.decimalCell(4, maintenance.cost);
                    row.dateCell(5, maintenance.maintenanceDateTime);
                    row.dateCell(6, maintenance.createdDateTime);
                    row.stringCell(7, imagesByMaintenanceId.get(maintenance.id));
                }
                lastId = page.get(page.size() - 1).id;
            }
        }
    }
}
