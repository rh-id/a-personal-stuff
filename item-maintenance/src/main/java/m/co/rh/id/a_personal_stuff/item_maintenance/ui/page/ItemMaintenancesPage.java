package m.co.rh.id.a_personal_stuff.item_maintenance.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;

import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.item_maintenance.R;
import m.co.rh.id.a_personal_stuff.item_maintenance.ui.component.ItemMaintenanceListSV;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavRoute;

public class ItemMaintenancesPage extends StatefulView<Activity> implements RequireNavRoute, Toolbar.OnMenuItemClickListener {

    @NavInject
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ItemMaintenanceListSV mItemMaintenanceListSV;

    public ItemMaintenancesPage() {
        mAppBarSV = new AppBarSV(R.menu.page_item_maintenances);
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
        if (mItemMaintenanceListSV == null) {
            mItemMaintenanceListSV = new ItemMaintenanceListSV(getItemId());
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_maintenances, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.title_maintenances));
        mAppBarSV.setMenuItemClick(this);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, containerAppBar));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mItemMaintenanceListSV.buildView(activity, containerContent));
        return rootLayout;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add) {
            mNavigator.push(Routes.ITEM_MAINTENANCE_DETAIL_PAGE,
                    ItemMaintenanceDetailPage.Args.with(getItemId()));
        }
        return false;
    }

    private Long getItemId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemId;
        }
        return null;
    }

    public static class Args implements Serializable {
        public static Args with(long itemId) {
            Args args = new Args();
            args.itemId = itemId;
            return args;
        }

        static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable args = navRoute.getRouteArgs();
                if (args instanceof Args) {
                    return (Args) args;
                }
            }
            return null;
        }

        private Long itemId;
    }
}
