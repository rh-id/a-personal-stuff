package m.co.rh.id.a_personal_stuff.item_checklist.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.item_checklist.R;
import m.co.rh.id.a_personal_stuff.item_checklist.ui.component.ItemChecklistListSV;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemChecklistsPage extends StatefulView<Activity> implements RequireComponent<Provider>, Toolbar.OnMenuItemClickListener {

    @NavInject
    private transient INavigator mNavigator;

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ItemChecklistListSV mItemChecklistListSV;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient SettingsSharedPreferences mSettingsSharedPreferences;
    private Args mArgs;

    public ItemChecklistsPage() {
        this(null);
    }

    public ItemChecklistsPage(Args args) {
        mArgs = args;
        if (args != null) {
            mAppBarSV = new AppBarSV();
            mItemChecklistListSV = new ItemChecklistListSV(args.itemId);
        } else {
            mAppBarSV = new AppBarSV(R.menu.menu_page_item_checklists);
            mItemChecklistListSV = new ItemChecklistListSV();
        }
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mSettingsSharedPreferences = mSvProvider.get(SettingsSharedPreferences.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_checklists, container, false);
        if (mArgs != null) {
            mAppBarSV.setTitle(activity.getString(R.string.title_checklists_with_, mArgs.itemTitle));
        } else {
            mAppBarSV.setTitle(activity.getString(R.string.title_checklists));
        }
        mAppBarSV.setMenuItemClick(this);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSV.buildView(activity, appBarContainer));
        if (mArgs == null) {
            mRxDisposer.add("createView_itemViewModeChanged",
                    mSettingsSharedPreferences.getItemViewModeFlow()
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(mode -> mAppBarSV.setMenuItemTitle(R.id.menu_view_mode,
                                    mode == SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT
                                            ? R.string.title_view_mode_detailed
                                            : R.string.title_view_mode_compact)));
        }
        ViewGroup contentContainer = rootLayout.findViewById(R.id.container_content);
        contentContainer.addView(mItemChecklistListSV.buildView(activity, contentContainer));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mItemChecklistListSV != null) {
            mItemChecklistListSV.dispose(activity);
            mItemChecklistListSV = null;
        }
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add) {
            mNavigator.push(Routes.ITEM_CHECKLIST_ADD_PAGE);
            return true;
        } else if (id == R.id.menu_view_mode) {
            toggleViewMode();
            return true;
        }
        return false;
    }

    /**
     * Flip between detailed and compact checklist cards. The selected mode is
     * persisted and pushed to the lists via the settings flow; the menu label
     * is kept in sync by the flow subscription in createView.
     */
    private void toggleViewMode() {
        boolean nowCompact = mSettingsSharedPreferences.getItemViewMode()
                != SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT;
        mSettingsSharedPreferences.setItemViewMode(
                nowCompact ? SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT
                        : SettingsSharedPreferences.ITEM_VIEW_MODE_DETAILED);
    }

    public static class Args implements Serializable {
        public static Args with(Long itemId, String itemTitle) {
            Args args = new Args();
            args.itemId = itemId;
            args.itemTitle = itemTitle;
            return args;
        }

        public static Args of(Serializable args) {
            if (args instanceof Args) {
                return (Args) args;
            }
            return null;
        }

        private Long itemId;
        private String itemTitle;
    }
}
