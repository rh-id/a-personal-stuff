package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_personal_stuff.app.ui.component.item.ItemListSV;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.SelectionPage;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavPopCallback;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemsPage extends StatefulView<Activity> implements RequireComponent<Provider>, NavPopCallback<Activity>, Toolbar.OnMenuItemClickListener {

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
    private SerialBehaviorSubject<Integer> mSelectedSort;

    public ItemsPage() {
        mAppBarSV = new AppBarSV(R.menu.page_items);
        mItemListSV = new ItemListSV();
        mSelectedSort = new SerialBehaviorSubject<>(0);
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mAppNotificationHandler = mSvProvider.get(AppNotificationHandler.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mRxDisposer.add("provideComponent_onSelectedSortChanged",
                mSelectedSort.getSubject()
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .subscribe(integer -> {
                            /*
                                        integers.add(R.string.sort_by_default);
                                        integers.add(R.string.sort_by_expired_date_time_asc);
                                        integers.add(R.string.sort_by_expired_date_time_desc);
                                        integers.add(R.string.sort_by_updated_date_time_asc);
                                        integers.add(R.string.sort_by_updated_date_time_desc);
                                        integers.add(R.string.sort_by_created_date_time_asc);
                                        integers.add(R.string.sort_by_created_date_time_desc);
                             */
                            switch (integer) {
                                case 1:
                                    mItemListSV.orderItemByExpiredTimeDate();
                                    break;
                                case 2:
                                    mItemListSV.orderItemByExpiredDateTimeDesc();
                                    break;
                                case 3:
                                    mItemListSV.orderItemByUpdatedDateTime();
                                    break;
                                case 4:
                                    mItemListSV.orderItemByUpdatedDateTimeDesc();
                                    break;
                                case 5:
                                    mItemListSV.orderItemByCreatedDateTime();
                                    break;
                                case 6:
                                    mItemListSV.orderItemByCreatedDateTimeDesc();
                                    break;
                                default:
                                    mItemListSV.resetOrderItem();
                            }
                        })
        );
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
        } else if (id == R.id.menu_sort) {
            ArrayList<Integer> integers = new ArrayList<>();
            integers.add(R.string.sort_by_default);
            integers.add(R.string.sort_by_expired_date_time_asc);
            integers.add(R.string.sort_by_expired_date_time_desc);
            integers.add(R.string.sort_by_updated_date_time_asc);
            integers.add(R.string.sort_by_updated_date_time_desc);
            integers.add(R.string.sort_by_created_date_time_asc);
            integers.add(R.string.sort_by_created_date_time_desc);
            mNavigator.push(Routes.COMMON_SELECTION, SelectionPage.Args.with(mSelectedSort.getValue(), integers),
                    this);
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

    @Override
    public void onPop(INavigator navigator, NavRoute navRoute, Activity activity, View currentView) {
        Serializable result = navRoute.getRouteResult();
        if (result instanceof Integer) {
            mSelectedSort.onNext((Integer) result);
        }
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
