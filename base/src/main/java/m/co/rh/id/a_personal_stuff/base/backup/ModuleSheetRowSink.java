package m.co.rh.id.a_personal_stuff.base.backup;

/**
 * Receives one row at a time from a module sheet's writer; :app creates the
 * physical row (streaming workbook) and adapts the filled cells.
 */
public interface ModuleSheetRowSink {

    /**
     * Creates the next data row and returns its cell writer; the caller
     * fills the cells for its columns and moves on to the next row.
     */
    ModuleSheetRow nextRow();
}
