package m.co.rh.id.a_personal_stuff.item_lending.provider.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import m.co.rh.id.a_personal_stuff.base.contributor.ModuleSheetRow;
import m.co.rh.id.a_personal_stuff.base.contributor.ModuleSheetRowSink;
import m.co.rh.id.a_personal_stuff.base.contributor.ModuleSpreadsheetContributor;
import m.co.rh.id.a_personal_stuff.base.contributor.ModuleSpreadsheetSheet;
import m.co.rh.id.a_personal_stuff.base.contributor.SpreadsheetJoinData;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLending;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLendingImage;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the "Lendings" sheet (keyset-paged rows with the per-page image
 * join), driven by :app's ExportService#createSpreadsheetXlsx.
 */
public class ItemLendingSpreadsheetContributor implements ModuleSpreadsheetContributor {

    private final ItemLendingDao mItemLendingDao;

    public ItemLendingSpreadsheetContributor(Provider provider) {
        mItemLendingDao = provider.get(ItemLendingDao.class);
    }

    @Override
    public List<ModuleSpreadsheetSheet> getSheets() {
        List<ModuleSpreadsheetSheet> sheets = new ArrayList<>();
        sheets.add(new LendingsSheet());
        return sheets;
    }

    private class LendingsSheet implements ModuleSpreadsheetSheet {

        @Override
        public String getName() {
            return "Lendings";
        }

        @Override
        public List<String> getHeaderKeys() {
            return Arrays.asList(
                    "header_id",
                    "header_item_id",
                    "header_item_name",
                    "header_item_lending_borrower",
                    "header_lend_date_time",
                    "header_due_date_time",
                    "header_returned_date_time",
                    "header_note",
                    "header_images");
        }

        /**
         * Lendings are keyset-paged: each iteration loads one page ordered by
         * id, fetches the page's images in a single query, writes its rows,
         * then advances {@code lastId} so the page can be garbage collected.
         */
        @Override
        public void writeRows(ModuleSheetRowSink rowSink, SpreadsheetJoinData joinData) {
            long lastId = 0L;
            List<ItemLending> page;
            while (!(page = mItemLendingDao.findItemLendingsAfter(lastId, PAGE_SIZE)).isEmpty()) {
                List<Long> pageIds = page.stream().map(lending -> lending.id)
                        .collect(Collectors.toList());
                Map<Long, String> imagesByLendingId = new HashMap<>();
                for (ItemLendingImage image : mItemLendingDao.findItemLendingImagesByItemLendingIds(pageIds)) {
                    if (image.itemLendingId == null || image.fileName == null || image.fileName.isEmpty()) {
                        continue;
                    }
                    String joined = imagesByLendingId.get(image.itemLendingId);
                    imagesByLendingId.put(image.itemLendingId,
                            joined == null || joined.isEmpty()
                                    ? image.fileName
                                    : joined + ", " + image.fileName);
                }
                for (ItemLending lending : page) {
                    ModuleSheetRow row = rowSink.nextRow();
                    row.longCell(0, lending.id);
                    row.longCell(1, lending.itemId);
                    row.stringCell(2, joinData.itemName(lending.itemId));
                    row.stringCell(3, lending.borrowerName);
                    row.dateCell(4, lending.lendDateTime);
                    row.dateCell(5, lending.dueDateTime);
                    row.dateCell(6, lending.returnedDateTime);
                    row.stringCell(7, lending.note);
                    row.stringCell(8, imagesByLendingId.get(lending.id));
                }
                lastId = page.get(page.size() - 1).id;
            }
        }
    }
}
