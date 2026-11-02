package m.co.rh.id.a_personal_stuff.base.contributor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * One dated line a module contributes to an item's PDF detail block (see
 * {@link ModulePdfContributor#getRecords()}): the module decides the ordering
 * of its own record list — :app concatenates the lists in contributor
 * registration order and renders one line per record. {@code labelKey} is the
 * stable :app string-resource NAME naming the record kind (same convention as
 * {@link ModuleSpreadsheetSheet#getHeaderKeys}: the resources live in :app,
 * which feature modules must not depend on). {@code dateTime} null skips the
 * line entirely (the undated case), a null {@code amount} renders as "-"
 * (mirrors the report's other null-cost handling), and a non-null
 * {@code note} renders as an indented second line under the record line
 * (free-text USER data, e.g. "Replaced broken one" — never localized by the
 * module).
 */
public final class ItemPdfRecord {

    /** Record date; null skips the line entirely. */
    public final Date dateTime;
    /** Record amount; null renders as "-". */
    public final BigDecimal amount;
    /** :app string-resource key NAME naming the record kind. Not null. */
    public final String labelKey;
    /** Optional free-text USER note; rendered as an indented second line. */
    public final String note;

    public ItemPdfRecord(Date dateTime, BigDecimal amount, String labelKey, String note) {
        this.dateTime = dateTime;
        this.amount = amount;
        this.labelKey = labelKey;
        this.note = note;
    }
}
