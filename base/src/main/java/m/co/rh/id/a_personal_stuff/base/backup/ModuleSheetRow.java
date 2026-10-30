package m.co.rh.id.a_personal_stuff.base.backup;

import java.math.BigDecimal;
import java.util.Date;

/**
 * POI-free typed cell writer a contributor fills for one sheet row; :app
 * adapts it to the streaming workbook (cells, styles, column-width
 * tracking). Column indexes are the contributor's own, matching
 * {@link ModuleSpreadsheetSheet#getHeaderKeys()} order.
 */
public interface ModuleSheetRow {

    /**
     * Writes an ID column as TEXT so values export losslessly and never
     * render in scientific notation.
     */
    void longCell(int column, Long value);

    void stringCell(int column, String value);

    /** True numeric cell for non-null integer amounts. */
    void amountCell(int column, int value);

    /** True numeric cell for nullable decimals (blank when null). */
    void decimalCell(int column, BigDecimal value);

    /** Date cell rendered with the shared "yyyy-mm-dd HH:mm:ss" format. */
    void dateCell(int column, Date value);
}
