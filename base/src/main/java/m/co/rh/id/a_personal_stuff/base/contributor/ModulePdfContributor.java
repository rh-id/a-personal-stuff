package m.co.rh.id.a_personal_stuff.base.contributor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * PDF-report contributor: a data-bearing module contributes dated records
 * and a summary-table date column for the PDF inventory report (driven by
 * :app's ExportService#createReportPdf), so :app keeps the layout/pagination/
 * drawing but no module ever imports another's DAO. Return empty maps when
 * the module has nothing to report.
 * <p>
 * The merge in :app is STRUCTURAL — there is no collision policy because two
 * modules contributing to the same item is the expected case: summary columns
 * are appended in contributor registration order at the summary table's
 * date-column anchor, and per-item record lists are concatenated per item id
 * in contributor registration order. Keys/labels are :app-owned stable string
 * resource NAMES ({@code getSummaryColumnKey}, {@link ItemPdfRecord#labelKey}
 * — same convention as {@link ModuleSpreadsheetSheet#getHeaderKeys}), which
 * :app resolves to the localized strings because the resources live in :app,
 * which feature modules must not depend on.
 * <p>
 * Most implementations eagerly resolve their DAO via {@code provider.get}
 * in the constructor — see {@link ModuleBackupContributor}'s javadoc for
 * the lazy-Provider escape hatch if startup order matters.
 */
public interface ModulePdfContributor {

    /**
     * :app string-resource key NAME for this module's summary-table date
     * column header; null = the module contributes no column.
     */
    String getSummaryColumnKey();

    /**
     * Per-item date for this module's summary-table column, keyed by item
     * id; no entry = blank cell.
     */
    Map<Long, Date> getSummaryDates();

    /**
     * Per-item dated records for the item's detail block, keyed by item id;
     * no entry or an empty list = no lines. The module decides the ordering
     * of its own list.
     */
    Map<Long, List<ItemPdfRecord>> getRecords();
}
