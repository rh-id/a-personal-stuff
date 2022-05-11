package m.co.rh.id.a_personal_stuff.base.ui.component;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
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
    private Integer mMenuResId;
    private transient Toolbar.OnMenuItemClickListener mOnMenuItemClickListener;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private SerialBehaviorSubject<String> mUpdateTitle;

    public AppBarSV() {
        this(null);
    }

    public AppBarSV(Integer menuResId) {
        mMenuResId = menuResId;
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
        mSvProvider = BaseApplication.of(navigator.getActivity()).getProvider()
                .get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        if (mUpdateTitle == null) {
            mUpdateTitle = new SerialBehaviorSubject<>();
        }
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
        if (mMenuResId != null) {
            toolbar.inflateMenu(mMenuResId);
        }
        toolbar.setOnMenuItemClickListener(this);
        mRxDisposer.add("createView_updateTitle",
                mUpdateTitle.getSubject().subscribe(toolbar::setTitle));
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
}
