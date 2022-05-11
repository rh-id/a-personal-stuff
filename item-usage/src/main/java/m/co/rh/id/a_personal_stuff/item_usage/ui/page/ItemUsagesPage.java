package m.co.rh.id.a_personal_stuff.item_usage.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;

import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.item_usage.R;
import m.co.rh.id.a_personal_stuff.item_usage.ui.component.ItemUsageListSV;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavRoute;

public class ItemUsagesPage extends StatefulView<Activity> implements RequireNavRoute, Toolbar.OnMenuItemClickListener {

    @NavInject
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ItemUsageListSV mItemUsageListSV;

    public ItemUsagesPage() {
        mAppBarSV = new AppBarSV(R.menu.page_item_usages);
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
        if (mItemUsageListSV == null) {
            mItemUsageListSV = new ItemUsageListSV(getItemId());
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_usages, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.title_usages));
        mAppBarSV.setMenuItemClick(this);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSV.buildView(activity, appBarContainer));
        ViewGroup contentContainer = rootLayout.findViewById(R.id.container_content);
        contentContainer.addView(mItemUsageListSV.buildView(activity, contentContainer));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
    }

    private Long getItemId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemId;
        }
        return null;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add) {
            mNavigator.push(Routes.ITEM_USAGE_DETAIL_PAGE,
                    ItemUsageDetailPage.Args.with(getItemId()));
            return true;
        }
        return false;
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
