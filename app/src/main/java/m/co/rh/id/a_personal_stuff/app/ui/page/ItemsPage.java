package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.ui.component.item.ItemListSV;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;

public class ItemsPage extends StatefulView<Activity> implements Toolbar.OnMenuItemClickListener {

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ItemListSV mItemListSV;

    public ItemsPage() {
        mAppBarSV = new AppBarSV(R.menu.page_items);
        mItemListSV = new ItemListSV();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_items, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.title_items));
        mAppBarSV.setMenuItemClick(this);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, containerAppBar));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mItemListSV.buildView(activity, containerAppBar));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mItemListSV.dispose(activity);
        mItemListSV = null;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add) {
            mNavigator.push(Routes.ITEM_DETAIL_PAGE);
        }
        return false;
    }
}
