package m.co.rh.id.a_personal_stuff.base.backup;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered collector of the per-module contributors (backup/export/import,
 * spreadsheet sheets, dashboard inputs). Registered eagerly in
 * {@code BaseProviderModule} (a plain {@code register}) so the registry
 * exists before any feature module's {@code provides()} runs — feature
 * modules then register their contributors synchronously inside their own
 * {@code provides()}, never inside a registerAsync component (those
 * construct after all modules register and would miss the registration
 * window).
 * <p>
 * Iteration order follows feature-module registration order, which keeps
 * backup.json section order, spreadsheet sheet order and import order
 * stable (usage, purchase, maintenance, reminder, checklist).
 */
public class BackupContributorRegistry {

    private final List<ModuleBackupContributor> mBackupContributors = new ArrayList<>();
    private final List<ModuleSpreadsheetContributor> mSpreadsheetContributors = new ArrayList<>();
    private final List<ModuleDashboardContributor> mDashboardContributors = new ArrayList<>();

    public void register(ModuleBackupContributor contributor) {
        mBackupContributors.add(contributor);
    }

    public void register(ModuleSpreadsheetContributor contributor) {
        mSpreadsheetContributors.add(contributor);
    }

    public void register(ModuleDashboardContributor contributor) {
        mDashboardContributors.add(contributor);
    }

    public List<ModuleBackupContributor> getBackupContributors() {
        return new ArrayList<>(mBackupContributors);
    }

    public List<ModuleSpreadsheetContributor> getSpreadsheetContributors() {
        return new ArrayList<>(mSpreadsheetContributors);
    }

    public List<ModuleDashboardContributor> getDashboardContributors() {
        return new ArrayList<>(mDashboardContributors);
    }
}
