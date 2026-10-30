package m.co.rh.id.a_personal_stuff.base.backup;

import io.reactivex.rxjava3.core.Flowable;

import java.util.List;
import java.util.Map;

/**
 * Dashboard inputs a module contributes: change flows that must trigger a
 * dashboard refresh, and the module's signed per-item stock deltas for the
 * derived remaining stock (amount − Σusage + Σpurchase, matching the item
 * card badge). :app aggregates the deltas by summing per item id; the
 * derived stock itself is computed by
 * {@code m.co.rh.id.a_personal_stuff.app.provider.component.InventoryStatsCalculator}
 * which feeds both the dashboard and the alert digest — do not approximate it.
 * <p>
 * Modules that affect neither the dashboard refresh nor the remaining stock
 * (maintenance, checklist) simply do not register a dashboard contributor.
 */
public interface ModuleDashboardContributor {

    /**
     * Change flows (already mapped to {@code Object}) merged into the
     * debounced dashboard refresh trigger stream.
     */
    List<Flowable<Object>> getRefreshTriggers();

    /**
     * Signed per-item stock deltas keyed by item id: usage contributes
     * −Σamount, purchase contributes +Σamount. Called on every stats
     * recompute, so implementations must read fresh DB state.
     */
    Map<Long, Integer> getStockDeltas();
}
