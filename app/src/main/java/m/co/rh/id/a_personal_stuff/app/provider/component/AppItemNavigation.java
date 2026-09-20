package m.co.rh.id.a_personal_stuff.app.provider.component;

import m.co.rh.id.a_personal_stuff.app.ui.page.ItemsPage;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemNavigation;
import m.co.rh.id.anavigator.component.INavigator;

/**
 * App-side implementation of {@link ItemNavigation}: pushes the items page in
 * filtered mode (single item + named filter chip). The push is synchronous on
 * the caller's (UI) thread.
 */
public class AppItemNavigation implements ItemNavigation {

    @Override
    public void pushItemListFiltered(INavigator navigator, long itemId, String itemName) {
        navigator.push(Routes.ITEMS_PAGE, ItemsPage.Args.filtered(itemId, itemName));
    }
}
