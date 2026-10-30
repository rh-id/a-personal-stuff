package m.co.rh.id.a_personal_stuff.base.backup;

import java.util.List;

/**
 * One spreadsheet sheet definition owned by a module: sheet name, header
 * keys and the (keyset-paged) row writer.
 */
public interface ModuleSpreadsheetSheet {

    /**
     * Stable sheet name, unchanged since the first export format.
     */
    String getName();

    /**
     * Header keys in column order: the stable :app string resource names the
     * sheet's headers have always used (e.g. "header_item_id"). :app resolves
     * them to the localized strings because the resources live in :app, which
     * feature modules must not depend on.
     */
    List<String> getHeaderKeys();

    /**
     * Writes the sheet's data rows, starting right below the header row.
     * High-volume sheets keyset-page their reads
     * ({@code WHERE id > :lastId ORDER BY id ASC LIMIT PAGE_SIZE}), writing
     * and dropping one page at a time to bound peak memory.
     */
    void writeRows(ModuleSheetRowSink rowSink, SpreadsheetJoinData joinData);
}
