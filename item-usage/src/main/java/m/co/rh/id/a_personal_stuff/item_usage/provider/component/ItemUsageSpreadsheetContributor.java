package m.co.rh.id.a_personal_stuff.item_usage.provider.component;

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
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the "Usages" sheet (keyset-paged rows with the per-page image
 * join), replacing the hard-coded writer in :app's ExportSpreadsheetCmd.
 */
public class ItemUsageSpreadsheetContributor implements ModuleSpreadsheetContributor {

    private final ItemUsageDao mItemUsageDao;

    public ItemUsageSpreadsheetContributor(Provider provider) {
        mItemUsageDao = provider.get(ItemUsageDao.class);
    }

    @Override
    public List<ModuleSpreadsheetSheet> getSheets() {
        List<ModuleSpreadsheetSheet> sheets = new ArrayList<>();
        sheets.add(new UsagesSheet());
        return sheets;
    }

    private class UsagesSheet implements ModuleSpreadsheetSheet {

        @Override
        public String getName() {
            return "Usages";
        }

        @Override
        public List<String> getHeaderKeys() {
            return Arrays.asList(
                    "header_id",
                    "header_item_id",
                    "header_item_name",
                    "form_description",
                    "form_amount",
                    "header_usage_date_time",
                    "header_created_date_time",
                    "header_images");
        }

        /**
         * Usages are keyset-paged: each iteration loads one page ordered by
         * id, fetches the page's images in a single query, writes its rows,
         * then advances {@code lastId} so the page can be garbage collected.
         */
        @Override
        public void writeRows(ModuleSheetRowSink rowSink, SpreadsheetJoinData joinData) {
            long lastId = 0L;
            List<ItemUsage> page;
            while (!(page = mItemUsageDao.findItemUsagesAfter(lastId, PAGE_SIZE)).isEmpty()) {
                List<Long> pageIds = page.stream().map(usage -> usage.id)
                        .collect(Collectors.toList());
                Map<Long, String> imagesByUsageId = new HashMap<>();
                for (ItemUsageImage image : mItemUsageDao.findItemUsageImagesByItemUsageIds(pageIds)) {
                    if (image.itemUsageId == null || image.fileName == null || image.fileName.isEmpty()) {
                        continue;
                    }
                    String joined = imagesByUsageId.get(image.itemUsageId);
                    imagesByUsageId.put(image.itemUsageId,
                            joined == null || joined.isEmpty()
                                    ? image.fileName
                                    : joined + ", " + image.fileName);
                }
                for (ItemUsage usage : page) {
                    ModuleSheetRow row = rowSink.nextRow();
                    row.longCell(0, usage.id);
                    row.longCell(1, usage.itemId);
                    row.stringCell(2, joinData.itemName(usage.itemId));
                    row.stringCell(3, usage.description);
                    row.amountCell(4, usage.amount);
                    row.dateCell(5, usage.usageDateTime);
                    row.dateCell(6, usage.createdDateTime);
                    row.stringCell(7, imagesByUsageId.get(usage.id));
                }
                lastId = page.get(page.size() - 1).id;
            }
        }
    }
}
