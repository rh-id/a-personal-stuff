package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_personal_stuff.app.ui.component.item.ItemListSV;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemsPage extends StatefulView<Activity> implements RequireComponent<Provider>, Toolbar.OnMenuItemClickListener {

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient AppNotificationHandler mAppNotificationHandler;
    private transient RxDisposer mRxDisposer;


    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ItemListSV mItemListSV;

    public ItemsPage() {
        mAppBarSV = new AppBarSV(R.menu.page_items);
        mItemListSV = new ItemListSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mAppNotificationHandler = mSvProvider.get(AppNotificationHandler.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        Long itemId = getItemId();
        if (itemId != null) {
            mItemListSV.showItemId(itemId);
        }
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
        mRxDisposer.add("createView_onNotificationEvent",
                mAppNotificationHandler.getItemReminderFlow().observeOn(Schedulers.from(mExecutorService))
                        .subscribe(itemReminder -> mItemListSV.showItemId(itemReminder.itemId)));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
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

    private Long getItemId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemId;
        }
        return null;
    }

    public static class Args implements Serializable {
        public static Args showItem(long itemId) {
            Args args = new Args();
            args.itemId = itemId;
            return args;
        }

        static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable arg = navRoute.getRouteArgs();
                if (arg instanceof Args) {
                    return (Args) arg;
                }
            }
            return null;
        }

        private Long itemId;
    }
}
