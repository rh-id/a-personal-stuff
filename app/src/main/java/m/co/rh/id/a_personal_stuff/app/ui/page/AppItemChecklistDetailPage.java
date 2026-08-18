package m.co.rh.id.a_personal_stuff.app.ui.page;

import java.util.ArrayList;

import m.co.rh.id.a_personal_stuff.app.ui.page.ItemMultiSelectPage.Result;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.item_checklist.ui.page.ItemChecklistDetailPage;
import m.co.rh.id.anavigator.NavRoute;

public class AppItemChecklistDetailPage extends ItemChecklistDetailPage {

    @Override
    protected void onAddItemsMenuClicked() {
        getNavigator().push(Routes.ITEM_MULTI_SELECT_PAGE,
                (navigator, navRoute, activity, currentView) -> onItemsSelected(navRoute));
    }

    private void onItemsSelected(NavRoute navRoute) {
        Result result = Result.of(navRoute);
        if (result != null) {
            ArrayList<ItemState> itemStates = result.getItemStates();
            if (itemStates != null && !itemStates.isEmpty()) {
                addItemChecklistItems(itemStates);
            }
        }
    }
}
