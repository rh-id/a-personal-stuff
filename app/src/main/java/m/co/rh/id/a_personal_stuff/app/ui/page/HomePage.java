package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.item_maintenance.ui.page.ItemMaintenanceDetailPage;
import m.co.rh.id.a_personal_stuff.item_reminder.ui.page.ItemReminderDetailPage;
import m.co.rh.id.a_personal_stuff.item_usage.ui.page.ItemUsageDetailPage;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class HomePage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnBackPressed<Activity>, DrawerLayout.DrawerListener, View.OnClickListener {
    private static final String TAG = HomePage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    private boolean mIsDrawerOpen;
    private transient long mLastBackPressMilis;

    // component
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;

    // View related
    private transient DrawerLayout mDrawerLayout;
    private transient View.OnClickListener mOnNavigationClicked;

    public HomePage() {
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mOnNavigationClicked = view -> {
            if (!mDrawerLayout.isOpen()) {
                mDrawerLayout.open();
            }
        };
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_home, container, false);
        View menuItems = rootLayout.findViewById(R.id.menu_items);
        menuItems.setOnClickListener(this);
        View menuSettings = rootLayout.findViewById(R.id.menu_settings);
        menuSettings.setOnClickListener(this);
        View menuDonation = rootLayout.findViewById(R.id.menu_donation);
        menuDonation.setOnClickListener(this);
        mDrawerLayout = rootLayout.findViewById(R.id.drawer);
        mDrawerLayout.addDrawerListener(this);
        mAppBarSV.setTitle(activity.getString(R.string.home));
        mAppBarSV.setNavigationOnClick(mOnNavigationClicked);
        if (mIsDrawerOpen) {
            mDrawerLayout.open();
        }
        Button addItemButton = rootLayout.findViewById(R.id.button_add_item);
        addItemButton.setOnClickListener(this);
        Button addItemUsageButton = rootLayout.findViewById(R.id.button_add_item_usage);
        addItemUsageButton.setOnClickListener(this);
        Button addItemMaintenanceButton = rootLayout.findViewById(R.id.button_add_item_maintenance);
        addItemMaintenanceButton.setOnClickListener(this);
        Button addItemReminderButton = rootLayout.findViewById(R.id.button_add_item_reminder);
        addItemReminderButton.setOnClickListener(this);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mDrawerLayout = null;
        mOnNavigationClicked = null;
    }

    @Override
    public void onBackPressed(View currentView, Activity activity, INavigator navigator) {
        if (mDrawerLayout.isOpen()) {
            mDrawerLayout.close();
        } else {
            long currentMilis = System.currentTimeMillis();
            if ((currentMilis - mLastBackPressMilis) < 1000) {
                navigator.finishActivity(null);
            } else {
                mLastBackPressMilis = currentMilis;
                mSvProvider.get(ILogger.class).i(TAG,
                        activity.getString(R.string.toast_back_press_exit));
            }
        }
    }

    @Override
    public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
        // Leave blank
    }

    @Override
    public void onDrawerOpened(@NonNull View drawerView) {
        mIsDrawerOpen = true;
    }

    @Override
    public void onDrawerClosed(@NonNull View drawerView) {
        mIsDrawerOpen = false;
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        // Leave blank
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.menu_items) {
            mNavigator.push(Routes.ITEMS_PAGE);
        } else if (id == R.id.menu_settings) {
            mNavigator.push(Routes.SETTINGS_PAGE);
        } else if (id == R.id.menu_donation) {
            mNavigator.push(Routes.DONATIONS_PAGE);
        } else if (id == R.id.button_add_item) {
            mNavigator.push(Routes.ITEM_DETAIL_PAGE);
        } else if (id == R.id.button_add_item_usage) {
            mNavigator.push(Routes.ITEM_SELECT_PAGE,
                    (navigator, navRoute, activity, currentView) -> itemSelectedForUsage(navRoute));
        } else if (id == R.id.button_add_item_maintenance) {
            mNavigator.push(Routes.ITEM_SELECT_PAGE,
                    (navigator, navRoute, activity, currentView) -> itemSelectedForMaintenance(navRoute));
        } else if (id == R.id.button_add_item_reminder) {
            mNavigator.push(Routes.ITEM_SELECT_PAGE,
                    (navigator, navRoute, activity, currentView) -> itemSelectedForReminder(navRoute));
        }
    }

    private void itemSelectedForReminder(NavRoute navRoute) {
        ItemSelectPage.Result result = ItemSelectPage.Result.of(navRoute);
        if (result != null) {
            ItemState itemState = result.getItemState();
            mNavigator.push(Routes.ITEM_REMINDER_DETAIL_PAGE,
                    ItemReminderDetailPage.Args.with(itemState.getItemId()));
        }
    }

    private void itemSelectedForUsage(NavRoute navRoute) {
        ItemSelectPage.Result result = ItemSelectPage.Result.of(navRoute);
        if (result != null) {
            ItemState itemState = result.getItemState();
            mNavigator.push(Routes.ITEM_USAGE_DETAIL_PAGE,
                    ItemUsageDetailPage.Args.with(itemState.getItemId()));
        }
    }

    private void itemSelectedForMaintenance(NavRoute navRoute) {
        ItemSelectPage.Result result = ItemSelectPage.Result.of(navRoute);
        if (result != null) {
            ItemState itemState = result.getItemState();
            mNavigator.push(Routes.ITEM_MAINTENANCE_DETAIL_PAGE,
                    ItemMaintenanceDetailPage.Args.with(itemState.getItemId()));
        }
    }
}