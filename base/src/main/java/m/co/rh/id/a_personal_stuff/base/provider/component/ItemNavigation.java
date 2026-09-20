package m.co.rh.id.a_personal_stuff.base.provider.component;

import m.co.rh.id.anavigator.component.INavigator;

/**
 * THE home for cross-module item navigation actions: feature modules can
 * navigate to item surfaces without depending on :app page/args classes.
 * Future item-navigation needs should become methods here instead of new
 * interfaces (same decoupling style as the item-reminder module's
 * IItemReminderNotificationHandler). Implementations must push synchronously
 * on the caller's (UI) thread — no DB access is needed since callers supply
 * the context they already have.
 */
public interface ItemNavigation {

    /**
     * Opens the items list filtered to show only the item with the given id.
     * The item name (nullable) is shown on a clearable filter chip.
     */
    void pushItemListFiltered(INavigator navigator, long itemId, String itemName);
}
