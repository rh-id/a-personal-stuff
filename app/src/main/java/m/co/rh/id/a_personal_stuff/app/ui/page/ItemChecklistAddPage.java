package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.command.QueryItemCmd;
import m.co.rh.id.a_personal_stuff.app.ui.component.item.ItemListSV;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.NewItemChecklistCmd;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemChecklistAddPage extends StatefulView<Activity> implements RequireComponent<Provider>, Toolbar.OnMenuItemClickListener {
    private static final String TAG = ItemChecklistAddPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ItemListSV mItemListSV;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient QueryItemCmd mQueryItemCmd;
    private transient NewItemChecklistCmd mNewItemChecklistCmd;

    public ItemChecklistAddPage() {
        mAppBarSV = new AppBarSV(R.menu.menu_page_item_checklist_add);
        mItemListSV = new ItemListSV(false, true);
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mQueryItemCmd = mSvProvider.get(QueryItemCmd.class);
        mNewItemChecklistCmd = mSvProvider.get(NewItemChecklistCmd.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_checklist_add, container, false);
        TextInputLayout formTitle = rootLayout.findViewById(R.id.form_title);
        mRxDisposer.add("createView_titleValid",
                mNewItemChecklistCmd.getTitleValidFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) formTitle.setError(s);
                            else formTitle.setError(null);
                        }, throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)));
        ViewGroup content = rootLayout.findViewById(R.id.container_content);
        content.addView(mItemListSV.buildView(activity, content));
        mAppBarSV.setTitle(activity.getString(R.string.title_add_item_checklist));
        mAppBarSV.setMenuItemClick(this);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSV.buildView(activity, appBarContainer));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mItemListSV.dispose(activity);
        mItemListSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            save();
            return true;
        }
        return false;
    }

    private void save() {
        Activity activity = mNavigator.getActivity();
        if (activity == null) return;
        EditText titleEditText = activity.findViewById(R.id.edit_text_title);
        if (titleEditText == null) return;
        ItemChecklistState itemChecklistState = new ItemChecklistState();
        itemChecklistState.setTitle(titleEditText.getText().toString().trim());
        EditText descriptionEditText = activity.findViewById(R.id.edit_text_description);
        if (descriptionEditText != null) {
            String description = descriptionEditText.getText().toString().trim();
            itemChecklistState.setDescription(description.isEmpty() ? null : description);
        }
        if (!mNewItemChecklistCmd.valid(itemChecklistState)) {
            return;
        }
        Set<Long> selectedIds = mItemListSV.getSelectedIds();
        if (selectedIds.isEmpty()) {
            itemChecklistState.updateItemChecklistItems(new ArrayList<>());
            executeSave(itemChecklistState);
            return;
        }
        mRxDisposer.add("save_fetchItems",
                mQueryItemCmd.findItemStateByItemIds(new ArrayList<>(selectedIds))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((itemStates, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else {
                                List<ItemChecklistItem> itemChecklistItems = new ArrayList<>();
                                for (ItemState itemState : itemStates) {
                                    ItemChecklistItem item = new ItemChecklistItem();
                                    item.itemId = itemState.getItemId();
                                    item.checkedDateTime = null;
                                    item.createdDateTime = new Date();
                                    itemChecklistItems.add(item);
                                }
                                itemChecklistState.updateItemChecklistItems(itemChecklistItems);
                                executeSave(itemChecklistState);
                            }
                        }));
    }

    private void executeSave(ItemChecklistState itemChecklistState) {
        mRxDisposer.add("save",
                mNewItemChecklistCmd.execute(itemChecklistState)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((result, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else {
                                mLogger.i(TAG, mSvProvider.getContext().getString(R.string.success_add_checklist_,
                                        itemChecklistState.getTitle()));
                                mNavigator.pop();
                            }
                        }));
    }
}
