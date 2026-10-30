package m.co.rh.id.a_personal_stuff.base.backup;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Contract every data-bearing feature module implements so :app can back up,
 * export and import the module's data without knowing about it. Implementations
 * resolve their own DAOs/notifier/FileHelper from the {@code Provider} passed
 * to their constructor and are registered synchronously inside the module's
 * {@code ProviderModule.provides()}:
 * {@code provider.get(BackupContributorRegistry.class).register(new XBackupContributor(provider))}.
 * <p>
 * Integration completeness: registering a contributor covers backup/export/
 * import, but a new data-bearing module must ALSO add an OrphanSweep branch
 * (DISTINCT item_id query + chunked delete + sweep entry in
 * {@code m.co.rh.id.a_personal_stuff.app.provider.component.OrphanSweep}) so
 * rows orphaned by a crash between an item delete and the change-notifier
 * event are swept — the contributor registry and the sweep are the two
 * integration points for module data.
 */
public interface ModuleBackupContributor {

    /**
     * Top-level JSON keys this contributor owns inside backup.json, in the
     * order they are written. Exported backups must carry exactly these keys
     * (guarded by the key-parity test), and imports must tolerate a missing
     * key (treated as an empty section) for forward compatibility.
     */
    List<String> getJsonKeys();

    /**
     * Gathers the module's rows for one backup/export: the returned sections
     * are merged into the backup.json root by :app and the image dirs (with
     * their referenced file names) are zipped by :app.
     */
    ModuleExport exportData();

    /**
     * Restores the module's sections from the backup root: remaps old→new
     * item ids via {@code oldToNewItemId} (items are always imported first by
     * :app), inserts via the module's DAOs, restores image files from
     * {@code tempDir} (rows are inserted before their files are copied so
     * file-helper clean-up passes never see an orphan file), publishes the
     * module's change-notifier events, and — reminder module only —
     * reschedules the reminder WorkManager jobs. Rows whose parent item id is
     * absent from the map are skipped (orphans are never imported). A
     * JSONException aborts the whole import, matching the pre-contract
     * behavior.
     */
    void importJson(JSONObject root, Map<Long, Long> oldToNewItemId, File tempDir) throws JSONException;
}
