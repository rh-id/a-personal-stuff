package m.co.rh.id.a_personal_stuff.base.contributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered collector of the per-module contributors (backup/export/import,
 * spreadsheet sheets, dashboard inputs, PDF report data). Registered eagerly in
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
public class ModuleContributorRegistry {

    private final List<ModuleBackupContributor> mBackupContributors = new ArrayList<>();
    private final List<ModuleSpreadsheetContributor> mSpreadsheetContributors = new ArrayList<>();
    private final List<ModuleDashboardContributor> mDashboardContributors = new ArrayList<>();
    private final List<ModulePdfContributor> mPdfContributors = new ArrayList<>();

    public void register(ModuleBackupContributor contributor) {
        mBackupContributors.add(contributor);
    }

    public void register(ModuleSpreadsheetContributor contributor) {
        mSpreadsheetContributors.add(contributor);
    }

    public void register(ModuleDashboardContributor contributor) {
        mDashboardContributors.add(contributor);
    }

    public void register(ModulePdfContributor contributor) {
        mPdfContributors.add(contributor);
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

    public List<ModulePdfContributor> getPdfContributors() {
        return new ArrayList<>(mPdfContributors);
    }
}
