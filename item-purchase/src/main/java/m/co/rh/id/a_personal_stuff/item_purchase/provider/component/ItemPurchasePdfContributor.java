package m.co.rh.id.a_personal_stuff.item_purchase.provider.component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_personal_stuff.base.contributor.ItemPdfRecord;
import m.co.rh.id.a_personal_stuff.base.contributor.ModulePdfContributor;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.aprovider.Provider;

/**
 * Contributes each item's purchase history to the PDF inventory report,
 * driven by :app's ExportService#createReportPdf: the first-purchase date as
 * the module's summary-table column and one "First purchase" record line per
 * dated purchase (the module's DAO query yields exactly the first/earliest
 * purchase per item, so today the list holds one record per item — the
 * 3-method contract leaves room to widen this to the full purchase history
 * later). {@link ItemPurchase#getDescription()} is deliberately NOT surfaced
 * as the record note so the rendered report stays identical to the
 * pre-contract output; exposing it is a future feature, not a migration step.
 */
public class ItemPurchasePdfContributor implements ModulePdfContributor {

    private final ItemPurchaseDao mItemPurchaseDao;

    public ItemPurchasePdfContributor(Provider provider) {
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
    }

    @Override
    public String getSummaryColumnKey() {
        return "report_col_first_purchase";
    }

    @Override
    public Map<Long, Date> getSummaryDates() {
        Map<Long, Date> dateByItemId = new HashMap<>();
        // runs the same query getRecords() runs; deliberate — this contributor
        // outlives exports, so caching would go stale
        List<ItemPurchase> firstPurchases = mItemPurchaseDao.findFirstPurchasesPerItem();
        for (ItemPurchase itemPurchase : firstPurchases) {
            // the query filters purchase_date_time IS NOT NULL, so the rows
            // are dated by definition
            dateByItemId.put(itemPurchase.itemId, itemPurchase.purchaseDateTime);
        }
        return dateByItemId;
    }

    @Override
    public Map<Long, List<ItemPdfRecord>> getRecords() {
        Map<Long, List<ItemPdfRecord>> recordsByItemId = new HashMap<>();
        List<ItemPurchase> firstPurchases = mItemPurchaseDao.findFirstPurchasesPerItem();
        for (ItemPurchase itemPurchase : firstPurchases) {
            List<ItemPdfRecord> records = recordsByItemId.get(itemPurchase.itemId);
            if (records == null) {
                records = new ArrayList<>();
                recordsByItemId.put(itemPurchase.itemId, records);
            }
            records.add(new ItemPdfRecord(itemPurchase.purchaseDateTime, itemPurchase.cost,
                    "report_record_first_purchase", null));
        }
        return recordsByItemId;
    }
}
