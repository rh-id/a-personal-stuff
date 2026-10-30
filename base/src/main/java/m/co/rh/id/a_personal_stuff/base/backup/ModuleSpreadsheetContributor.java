package m.co.rh.id.a_personal_stuff.base.backup;

import java.util.List;

/**
 * Per-sheet spreadsheet contributor: a data-bearing module contributes its
 * own sheet(s) (name + headers + row writing) so :app no longer hard-codes
 * them. Sheet order is the contributor registration order followed by the
 * contributor's sheet order — the checklist module contributes two sheets in
 * a fixed order. The Items sheet stays owned by :app (base-DB anchor).
 */
public interface ModuleSpreadsheetContributor {

    /**
     * Keyset page size shared by every paging sheet. MUST stay below ~900:
     * the per-page image lookup uses {@code IN (:ids)} with one bind
     * parameter per id and SQLite caps a statement at 999 variables
     * (SQLITE_MAX_VARIABLE_NUMBER).
     */
    int PAGE_SIZE = 500;

    /**
     * The module's sheets in stable export order.
     */
    List<ModuleSpreadsheetSheet> getSheets();
}
