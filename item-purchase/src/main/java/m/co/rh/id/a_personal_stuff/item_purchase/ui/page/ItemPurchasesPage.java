package m.co.rh.id.a_personal_stuff.item_purchase.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;

import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.item_purchase.R;
import m.co.rh.id.a_personal_stuff.item_purchase.ui.component.ItemPurchaseListSV;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavRoute;

public class ItemPurchasesPage extends StatefulView<Activity> implements RequireNavRoute, Toolbar.OnMenuItemClickListener {

    @NavInject
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ItemPurchaseListSV mItemPurchaseListSV;

    public ItemPurchasesPage() {
        mAppBarSV = new AppBarSV(R.menu.page_item_purchases);
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
        if (mItemPurchaseListSV == null) {
            mItemPurchaseListSV = new ItemPurchaseListSV(getItemId());
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_purchases, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.title_purchases));
        mAppBarSV.setMenuItemClick(this);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSV.buildView(activity, appBarContainer));
        ViewGroup contentContainer = rootLayout.findViewById(R.id.container_content);
        contentContainer.addView(mItemPurchaseListSV.buildView(activity, contentContainer));
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
            mNavigator.push(Routes.ITEM_PURCHASE_DETAIL_PAGE,
                    ItemPurchaseDetailPage.Args.with(getItemId()));
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
