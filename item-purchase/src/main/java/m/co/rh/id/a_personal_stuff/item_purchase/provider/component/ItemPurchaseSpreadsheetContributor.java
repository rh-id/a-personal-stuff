package m.co.rh.id.a_personal_stuff.item_purchase.provider.component;

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
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes the "Purchases" sheet (keyset-paged rows with the per-page
 * image join), replacing the hard-coded writer in :app's
 * ExportSpreadsheetCmd.
 */
public class ItemPurchaseSpreadsheetContributor implements ModuleSpreadsheetContributor {

    private final ItemPurchaseDao mItemPurchaseDao;

    public ItemPurchaseSpreadsheetContributor(Provider provider) {
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
    }

    @Override
    public List<ModuleSpreadsheetSheet> getSheets() {
        List<ModuleSpreadsheetSheet> sheets = new ArrayList<>();
        sheets.add(new PurchasesSheet());
        return sheets;
    }

    private class PurchasesSheet implements ModuleSpreadsheetSheet {

        @Override
        public String getName() {
            return "Purchases";
        }

        @Override
        public List<String> getHeaderKeys() {
            return Arrays.asList(
                    "header_id",
                    "header_item_id",
                    "header_item_name",
                    "form_description",
                    "form_amount",
                    "header_cost",
                    "header_purchase_date_time",
                    "header_created_date_time",
                    "header_images");
        }

        /**
         * Purchases are keyset-paged like the Usages sheet (see the usage
         * contributor's javadoc).
         */
        @Override
        public void writeRows(ModuleSheetRowSink rowSink, SpreadsheetJoinData joinData) {
            long lastId = 0L;
            List<ItemPurchase> page;
            while (!(page = mItemPurchaseDao.findItemPurchasesAfter(lastId, PAGE_SIZE)).isEmpty()) {
                List<Long> pageIds = page.stream().map(purchase -> purchase.id)
                        .collect(Collectors.toList());
                Map<Long, String> imagesByPurchaseId = new HashMap<>();
                for (ItemPurchaseImage image : mItemPurchaseDao.findItemPurchaseImagesByItemPurchaseIds(pageIds)) {
                    if (image.itemPurchaseId == null || image.fileName == null || image.fileName.isEmpty()) {
                        continue;
                    }
                    String joined = imagesByPurchaseId.get(image.itemPurchaseId);
                    imagesByPurchaseId.put(image.itemPurchaseId,
                            joined == null || joined.isEmpty()
                                    ? image.fileName
                                    : joined + ", " + image.fileName);
                }
                for (ItemPurchase purchase : page) {
                    ModuleSheetRow row = rowSink.nextRow();
                    row.longCell(0, purchase.id);
                    row.longCell(1, purchase.itemId);
                    row.stringCell(2, joinData.itemName(purchase.itemId));
                    row.stringCell(3, purchase.description);
                    row.amountCell(4, purchase.amount);
                    row.decimalCell(5, purchase.cost);
                    row.dateCell(6, purchase.purchaseDateTime);
                    row.dateCell(7, purchase.createdDateTime);
                    row.stringCell(8, imagesByPurchaseId.get(purchase.id));
                }
                lastId = page.get(page.size() - 1).id;
            }
        }
    }
}
