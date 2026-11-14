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
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.ui.component.item.ItemListSV;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.SelectionPage;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavPopCallback;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * The archived-items list: a read/manage view over the ARCHIVED side of the
 * item table (unarchive from the card's archive button; the overflow popup is
 * hidden on archived cards, matching the card's archived state rules).
 * Modeled on {@link ItemsPage} minus everything only the active list needs
 * (no add menu, no filter chip, no scan button).
 */
public class ArchivedItemsPage extends StatefulView<Activity> implements RequireComponent<Provider>, NavPopCallback<Activity>, Toolbar.OnMenuItemClickListener {

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;
    private transient SettingsSharedPreferences mSettingsSharedPreferences;

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ItemListSV mItemListSV;
    private SerialBehaviorSubject<Integer> mSelectedSort;

    public ArchivedItemsPage() {
        mAppBarSV = new AppBarSV(R.menu.page_archived_items);
        mItemListSV = new ItemListSV(false, false, true);
        mSelectedSort = new SerialBehaviorSubject<>(0);
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mSettingsSharedPreferences = mSvProvider.get(SettingsSharedPreferences.class);
        mRxDisposer.add("provideComponent_onSelectedSortChanged",
                mSelectedSort.getSubject()
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .subscribe(integer -> {
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
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_archived_items, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.title_archived_items));
        mAppBarSV.setMenuItemClick(this);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, containerAppBar));
        mRxDisposer.add("createView_itemViewModeChanged",
                mSettingsSharedPreferences.getItemViewModeFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mode -> mAppBarSV.setMenuItemTitle(R.id.menu_view_mode,
                                mode == SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT
                                        ? R.string.title_view_mode_detailed
                                        : R.string.title_view_mode_compact)));
        ViewGroup containerContent = rootLayout.findViewById(R.id.container_content);
        containerContent.addView(mItemListSV.buildView(activity, containerAppBar));
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
        if (id == R.id.menu_sort) {
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
        } else if (id == R.id.menu_view_mode) {
            toggleViewMode();
        }
        return false;
    }

    /**
     * Flip between detailed and compact item cards. The selected mode is
     * persisted and pushed to {@link ItemListSV} via the settings flow; the menu
     * label is kept in sync by the flow subscription in createView.
     */
    private void toggleViewMode() {
        boolean nowCompact = mSettingsSharedPreferences.getItemViewMode()
                != SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT;
        mSettingsSharedPreferences.setItemViewMode(
                nowCompact ? SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT
                        : SettingsSharedPreferences.ITEM_VIEW_MODE_DETAILED);
    }

    @Override
    public void onPop(INavigator navigator, NavRoute navRoute, Activity activity, View currentView) {
        Serializable result = navRoute.getRouteResult();
        if (result instanceof Integer) {
            mSelectedSort.onNext((Integer) result);
        }
    }
}
