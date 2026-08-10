package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListPopupWindow;

import java.io.Serializable;
import java.util.Arrays;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.ui.component.StockMovementsListSV;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.item_purchase.ui.page.ItemPurchaseDetailPage;
import m.co.rh.id.a_personal_stuff.item_usage.ui.page.ItemUsageDetailPage;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavRoute;

public class ItemStockMovementsPage extends StatefulView<Activity> implements RequireNavRoute {

    @NavInject
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private StockMovementsListSV mStockMovementsListSV;

    public ItemStockMovementsPage() {
        // No toolbar menu — adding is via the floating action button.
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
        if (mStockMovementsListSV == null) {
            mStockMovementsListSV = new StockMovementsListSV(getItemId());
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_stock_movements, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.title_stock_movements));
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSV.buildView(activity, appBarContainer));
        ViewGroup contentContainer = rootLayout.findViewById(R.id.container_content);
        contentContainer.addView(mStockMovementsListSV.buildView(activity, contentContainer));
        View fabAdd = rootLayout.findViewById(R.id.fab_add);
        fabAdd.setOnClickListener(v -> showAddPopup(v));
        return rootLayout;
    }

    /**
     * Anchors a small popup at the FAB letting the user choose what to add.
     * Mirrors the ListPopupWindow pattern used for the item "more actions" menu.
     */
    private void showAddPopup(View anchor) {
        Context context = anchor.getContext();
        String addUsage = context.getString(
                m.co.rh.id.a_personal_stuff.item_usage.R.string.title_add_item_usage);
        String addPurchase = context.getString(
                m.co.rh.id.a_personal_stuff.item_purchase.R.string.title_add_item_purchase);
        ListPopupWindow popup = new ListPopupWindow(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, Arrays.asList(addUsage, addPurchase));
        popup.setAdapter(adapter);
        popup.setAnchorView(anchor);
        // Drop up from the FAB, end-aligned so it appears above-left of the button.
        popup.setDropDownGravity(android.view.Gravity.END | android.view.Gravity.BOTTOM);
        popup.setHeight(ListPopupWindow.WRAP_CONTENT);
        // simple_list_item_1 is match_parent-width, which ListPopupWindow's
        // internal measure collapses to ~0; force a width that fits the longest
        // label so the rows aren't truncated to a few characters.
        popup.setContentWidth(measureMaxContentWidth(context, adapter));
        popup.setOnItemClickListener((parent, view, position, id) -> {
            Long itemId = getItemId();
            if (position == 0) {
                mNavigator.push(Routes.ITEM_USAGE_DETAIL_PAGE,
                        ItemUsageDetailPage.Args.with(itemId));
            } else if (position == 1) {
                mNavigator.push(Routes.ITEM_PURCHASE_DETAIL_PAGE,
                        ItemPurchaseDetailPage.Args.with(itemId));
            }
            popup.dismiss();
        });
        popup.show();
    }

    /**
     * Measure the widest adapter row so the popup width fits the longest label.
     * Mirrors the same workaround used in ItemItemSV.showMoreActionList.
     */
    private static int measureMaxContentWidth(Context context, ArrayAdapter<String> adapter) {
        int maxWidth = 0;
        View measureView = null;
        for (int i = 0; i < adapter.getCount(); i++) {
            measureView = adapter.getView(i, measureView, null);
            // getView inflates simple_list_item_1 with a null parent, so the view
            // has no LayoutParams — TextView.checkForRelayout() crashes on setText
            // without one. Assign one before measuring.
            if (measureView.getLayoutParams() == null) {
                measureView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            measureView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int w = measureView.getMeasuredWidth();
            if (w > maxWidth) {
                maxWidth = w;
            }
        }
        // Add horizontal padding so labels aren't flush against the popup edges.
        float density = context.getResources().getDisplayMetrics().density;
        return maxWidth + (int) (32 * density);
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
