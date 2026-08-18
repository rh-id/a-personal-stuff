package m.co.rh.id.a_personal_stuff.base.ui.component;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.base.BaseApplication;
import m.co.rh.id.a_personal_stuff.base.R;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavRouteIndex;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class AppBarSV extends StatefulView<Activity> implements RequireNavigator, View.OnClickListener, Toolbar.OnMenuItemClickListener {

    private transient INavigator mNavigator;
    @NavRouteIndex
    private transient byte mRouteIndex;
    private transient View.OnClickListener mNavigationOnClickListener;
    private transient Toolbar.OnMenuItemClickListener mOnMenuItemClickListener;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private SerialBehaviorSubject<String> mUpdateTitle;
    private SerialBehaviorSubject<MenuItemTitle> mUpdateMenuItemTitle;
    private SerialBehaviorSubject<Integer> mMenuResId;

    public AppBarSV() {
        this(null);
    }

    public AppBarSV(Integer menuResId) {
        mUpdateTitle = new SerialBehaviorSubject<>();
        mUpdateMenuItemTitle = new SerialBehaviorSubject<>();
        mMenuResId = new SerialBehaviorSubject<>();
        if (menuResId != null) {
            mMenuResId.onNext(menuResId);
        }
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
        mSvProvider = BaseApplication.of(navigator.getActivity()).getProvider()
                .get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.app_bar, container, false);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        if (isInitialRoute()) {
            toolbar.setNavigationIcon(R.drawable.ic_menu_white);
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white);
        }
        toolbar.setNavigationOnClickListener(this);
        mRxDisposer.add("createView_updateMenu",
                mMenuResId.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(menuResId -> {
                            // clear() before re-inflating keeps repeated emissions
                            // (e.g. a menu swapped via setMenu) from stacking items.
                            toolbar.getMenu().clear();
                            toolbar.inflateMenu(menuResId);
                            // clear() resets item titles to their xml defaults, so
                            // re-apply the last queued menu-item title.
                            MenuItemTitle menuItemTitle = mUpdateMenuItemTitle.getValue();
                            if (menuItemTitle != null) {
                                mUpdateMenuItemTitle.onNext(menuItemTitle);
                            }
                        }));
        toolbar.setOnMenuItemClickListener(this);
        mRxDisposer.add("createView_updateTitle",
                mUpdateTitle.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(toolbar::setTitle));
        mRxDisposer.add("createView_updateMenuItemTitle",
                mUpdateMenuItemTitle.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(menuItemTitle -> {
                            MenuItem menuItem = toolbar.getMenu().findItem(menuItemTitle.menuItemId);
                            if (menuItem != null) {
                                menuItem.setTitle(menuItemTitle.titleResId);
                            }
                        }));
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mNavigationOnClickListener = null;
        mNavigator = null;
    }

    public boolean isInitialRoute() {
        return mRouteIndex == 0;
    }

    public void setTitle(String title) {
        mUpdateTitle.onNext(title);
    }

    public void setMenu(Integer menuResId) {
        if (menuResId != null) {
            mMenuResId.onNext(menuResId);
        }
    }

    public void setNavigationOnClick(View.OnClickListener navigationOnClickListener) {
        mNavigationOnClickListener = navigationOnClickListener;
    }

    public void setMenuItemClick(Toolbar.OnMenuItemClickListener listener) {
        mOnMenuItemClickListener = listener;
    }

    @Override
    public void onClick(View view) {
        if (isInitialRoute()) {
            if (mNavigationOnClickListener != null) {
                mNavigationOnClickListener.onClick(view);
            }
        } else {
            mNavigator.pop();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (mOnMenuItemClickListener != null) {
            return mOnMenuItemClickListener.onMenuItemClick(item);
        }
        return false;
    }

    /**
     * Update a menu item's title after the menu is inflated. The value is queued
     * like setTitle and applied when the view exists. No-op when the app bar
     * has no menu.
     */
    public void setMenuItemTitle(int menuItemId, int titleResId) {
        mUpdateMenuItemTitle.onNext(new MenuItemTitle(menuItemId, titleResId));
    }

    private static class MenuItemTitle implements Serializable {
        final int menuItemId;
        final int titleResId;

        MenuItemTitle(int menuItemId, int titleResId) {
            this.menuItemId = menuItemId;
            this.titleResId = titleResId;
        }
    }
}
