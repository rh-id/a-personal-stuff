package m.co.rh.id.a_personal_stuff.item_checklist.ui.page;

import android.app.Activity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.InputSVDialog;
import m.co.rh.id.a_personal_stuff.item_checklist.R;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.AddItemChecklistItemCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.DeleteItemChecklistItemCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.QueryItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.UpdateItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.UpdateItemChecklistItemCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_checklist.ui.component.ItemChecklistEntryRecyclerViewAdapter;
import m.co.rh.id.a_personal_stuff.item_checklist.ui.component.ItemChecklistEntrySV;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavPopCallback;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemChecklistDetailPage extends StatefulView<Activity> implements RequireComponent<Provider>, Toolbar.OnMenuItemClickListener, NavPopCallback<Activity> {

    private static final String TAG = ItemChecklistDetailPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;

    @NavInject
    private AppBarSV mAppBarSV;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;
    private transient ItemChecklistChangeNotifier mItemChecklistChangeNotifier;
    private transient QueryItemChecklistCmd mQueryItemChecklistCmd;
    private transient UpdateItemChecklistCmd mUpdateItemChecklistCmd;
    private transient AddItemChecklistItemCmd mAddItemChecklistItemCmd;
    private transient UpdateItemChecklistItemCmd mUpdateItemChecklistItemCmd;
    private transient DeleteItemChecklistItemCmd mDeleteItemChecklistItemCmd;
    private transient ItemDao mItemDao;

    private Long mChecklistId;
    private ItemChecklistState mItemChecklistState;
    private ArrayList<ItemChecklistEntrySV.ItemChecklistEntry> mEntries;
    private transient ItemChecklistEntryRecyclerViewAdapter mAdapter;
    private String mSearchQuery;

    private transient TextWatcher mSearchTextWatcher;

    public ItemChecklistDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.menu_page_item_checklist_detail);
        mEntries = new ArrayList<>();
        mSearchQuery = "";
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mItemChecklistChangeNotifier = mSvProvider.get(ItemChecklistChangeNotifier.class);
        mQueryItemChecklistCmd = mSvProvider.get(QueryItemChecklistCmd.class);
        mUpdateItemChecklistCmd = mSvProvider.get(UpdateItemChecklistCmd.class);
        mAddItemChecklistItemCmd = mSvProvider.get(AddItemChecklistItemCmd.class);
        mUpdateItemChecklistItemCmd = mSvProvider.get(UpdateItemChecklistItemCmd.class);
        mDeleteItemChecklistItemCmd = mSvProvider.get(DeleteItemChecklistItemCmd.class);
        mItemDao = mSvProvider.get(ItemDao.class);

        mSearchTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                mSearchQuery = editable.toString();
                filterEntries(editable.toString());
            }
        };
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        mChecklistId = getChecklistId();
        loadChecklist();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_checklist_detail, container, false);

        TextView progressText = rootLayout.findViewById(R.id.text_progress);
        EditText searchEditText = rootLayout.findViewById(R.id.edit_text_search);
        searchEditText.addTextChangedListener(mSearchTextWatcher);

        mAdapter = new ItemChecklistEntryRecyclerViewAdapter(
                this::onEntryDeleteClicked,
                this::onEntryCheckClicked,
                mNavigator, this);

        // Setup RecyclerView
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mAdapter);

        mAppBarSV.setMenuItemClick(this);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSV.buildView(activity, appBarContainer));

        // Listen to checklist changes
        mRxDisposer.add("createView_onChecklistChanged",
                mItemChecklistChangeNotifier.getAnyItemChecklistChangeFlow()
                        .filter(checklistId -> checklistId.getChecklistId().equals(mChecklistId))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(checklistState -> {
                            loadChecklist();
                        }));

        // Listen to item changes
        mRxDisposer.add("createView_onItemChanged",
                mItemChecklistChangeNotifier.getAnyItemChecklistItemChangeFlow()
                        .filter(checklistId -> checklistId.equals(mChecklistId))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(checklistId -> {
                            loadChecklist();
                        }));

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
        if (mAdapter != null) {
            mAdapter.dispose(activity);
            mAdapter = null;
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add_items) {
            onAddItemsMenuClicked();
            return true;
        } else if (id == R.id.menu_rename) {
            onRenameMenuClicked();
            return true;
        } else if (id == R.id.menu_edit_description) {
            onEditDescriptionMenuClicked();
            return true;
        }
        return false;
    }

    @Override
    public void onPop(INavigator navigator, NavRoute navRoute, Activity activity, View currentView) {
        // Base implementation does nothing
    }

    protected INavigator getNavigator() {
        return mNavigator;
    }

    protected void onAddItemsMenuClicked() {
        // Base implementation - to be overridden by app module subclass
        mLogger.i(TAG, "Add items - override in app module");
    }

    protected void onRenameMenuClicked() {
        Activity activity = mNavigator.getActivity();
        if (activity == null || mItemChecklistState == null) return;

        mNavigator.push(Routes.COMMON_INPUT_DIALOG,
                InputSVDialog.Args.newArgs(
                        activity.getString(R.string.title_rename_checklist),
                        activity.getString(R.string.hint_checklist_title),
                        mItemChecklistState.getTitle()),
                (navigator, navRoute, act, currentView) -> {
                    InputSVDialog.Result result = InputSVDialog.Result.of(navRoute);
                    if (result != null && !TextUtils.isEmpty(result.getText())) {
                        renameChecklist(result.getText().trim());
                    } else {
                        mLogger.i(TAG, act.getString(R.string.checklist_title_required));
                    }
                });
    }

    protected void onEditDescriptionMenuClicked() {
        Activity activity = mNavigator.getActivity();
        if (activity == null || mItemChecklistState == null) return;
        String description = mItemChecklistState.getDescription();
        mNavigator.push(Routes.COMMON_INPUT_DIALOG,
                InputSVDialog.Args.newArgs(
                        activity.getString(R.string.title_edit_checklist_description),
                        activity.getString(R.string.hint_checklist_description),
                        description != null ? description : "", true),
                (navigator, navRoute, act, currentView) -> {
                    InputSVDialog.Result result = InputSVDialog.Result.of(navRoute);
                    if (result != null) {
                        updateChecklistDescription(result.getText());
                    }
                });
    }

    private void updateChecklistDescription(String description) {
        if (mItemChecklistState == null) return;
        String trimmed = description != null ? description.trim() : "";
        mItemChecklistState.setDescription(trimmed.isEmpty() ? null : trimmed);
        updateChecklist();
    }

    public void addItemChecklistItems(List<ItemState> itemStates) {
        if (itemStates == null || itemStates.isEmpty()) return;

        Activity activity = mNavigator.getActivity();
        if (activity == null) return;

        List<Long> itemIds = itemStates.stream()
                .map(ItemState::getItemId)
                .collect(Collectors.toList());

        mRxDisposer.add("addItemChecklistItems",
                mAddItemChecklistItemCmd.execute(mChecklistId, itemIds)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((count, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else {
                                if (itemStates.size() == 1 && count != null && count == 1) {
                                    mLogger.i(TAG, activity.getString(R.string.success_add_item_to_checklist_,
                                            itemStates.get(0).getItemName()));
                                } else {
                                    mLogger.i(TAG, activity.getString(R.string.success_add_items, count));
                                }
                            }
                        }));
    }

    private void loadChecklist() {
        mRxDisposer.add("loadChecklist",
                mQueryItemChecklistCmd.findItemChecklistStateById(mChecklistId)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((itemChecklistState, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else if (itemChecklistState != null) {
                                mItemChecklistState = itemChecklistState;
                                updateHeader();
                                loadEntries();
                                loadProgress();
                            }
                        }));
    }

    private void updateHeader() {
        Activity activity = mNavigator.getActivity();
        if (activity != null && mItemChecklistState != null) {
            mAppBarSV.setTitle(mItemChecklistState.getTitle());
            TextView descriptionText = activity.findViewById(R.id.text_description);
            if (descriptionText != null) {
                String description = mItemChecklistState.getDescription();
                if (description != null && !description.isEmpty()) {
                    descriptionText.setText(description);
                    descriptionText.setVisibility(View.VISIBLE);
                } else {
                    descriptionText.setText(null);
                    descriptionText.setVisibility(View.GONE);
                }
            }
        }
    }

    private void loadProgress() {
        mRxDisposer.add("loadProgress",
                mQueryItemChecklistCmd.findProgressByChecklistId(mChecklistId)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((progress, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else {
                                Activity activity = mNavigator.getActivity();
                                if (activity != null) {
                                    TextView progressText = activity.findViewById(R.id.text_progress);
                                    if (progressText != null) {
                                        int checked = progress != null ? progress.checked : 0;
                                        int total = progress != null ? progress.total : 0;
                                        progressText.setText(activity.getString(R.string.checklist_progress,
                                                checked, total));
                                    }
                                }
                            }
                        }));
    }

    private void loadEntries() {
        if (mItemChecklistState == null) return;

        mRxDisposer.add("loadEntries",
                mQueryItemChecklistCmd.findItemsByChecklistId(mChecklistId)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((itemChecklistItems, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else {
                                loadItemStates(itemChecklistItems);
                            }
                        }));
    }

    private void loadItemStates(List<ItemChecklistItem> itemChecklistItems) {
        if (itemChecklistItems.isEmpty()) {
            mEntries.clear();
            updateAdapter();
            return;
        }

        List<Long> itemIds = itemChecklistItems.stream()
                .map(item -> item.itemId)
                .collect(Collectors.toList());

        mRxDisposer.add("loadItemStates",
                Single
                        .fromCallable(() -> mItemDao.findItemStatesByIds(itemIds))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((itemStates, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else {
                                mergeEntries(itemChecklistItems, itemStates);
                            }
                        }));
    }

    private void mergeEntries(List<ItemChecklistItem> itemChecklistItems, List<ItemState> itemStates) {
        mEntries.clear();

        // Create a map for quick lookup
        Map<Long, ItemState> itemStateMap = itemStates.stream()
                .collect(Collectors.toMap(ItemState::getItemId, state -> state));

        for (ItemChecklistItem itemChecklistItem : itemChecklistItems) {
            ItemState itemState = itemStateMap.get(itemChecklistItem.itemId);
            if (itemState != null) {
                mEntries.add(new ItemChecklistEntrySV.ItemChecklistEntry(
                        itemChecklistItem, itemState));
            }
            // Skip items that don't exist (deleted items)
        }

        updateAdapter();
    }

    private void updateAdapter() {
        if (mAdapter != null) {
            mAdapter.setEntries(filteredEntries());
        }
    }

    private ArrayList<ItemChecklistEntrySV.ItemChecklistEntry> filteredEntries() {
        if (TextUtils.isEmpty(mSearchQuery)) {
            return mEntries;
        }
        ArrayList<ItemChecklistEntrySV.ItemChecklistEntry> filtered = new ArrayList<>();
        for (ItemChecklistEntrySV.ItemChecklistEntry entry : mEntries) {
            if (entry.itemState != null && entry.itemState.getItemName() != null &&
                    entry.itemState.getItemName().toLowerCase().contains(mSearchQuery.toLowerCase())) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private void filterEntries(String query) {
        mSearchQuery = query;
        if (mAdapter != null) {
            mAdapter.setEntries(filteredEntries());
        }
    }

    private void renameChecklist(String title) {
        if (mItemChecklistState == null) return;
        mItemChecklistState.setTitle(title);
        updateChecklist();
    }

    private void updateChecklist() {
        mRxDisposer.add("updateChecklist",
                mUpdateItemChecklistCmd.execute(mItemChecklistState)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((updatedState, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else {
                                Activity activity = mNavigator.getActivity();
                                if (activity != null) {
                                    mLogger.i(TAG, activity.getString(R.string.success_update_checklist_,
                                            mItemChecklistState.getTitle()));
                                }
                            }
                        }));
    }

    private void onEntryDeleteClicked(ItemChecklistEntrySV.ItemChecklistEntry entry) {
        if (entry.itemChecklistItem == null) return;

        Activity activity = mNavigator.getActivity();
        if (activity == null) return;

        mRxDisposer.add("onEntryDeleteClicked",
                mDeleteItemChecklistItemCmd.execute(entry.itemChecklistItem)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((deletedItem, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else {
                                mLogger.i(TAG, activity.getString(R.string.success_remove_item_,
                                        entry.itemState != null ? entry.itemState.getItemName() : ""));
                            }
                        }));
    }

    private void onEntryCheckClicked(ItemChecklistEntrySV.ItemChecklistEntry entry) {
        if (entry.itemChecklistItem == null) return;

        // Toggle check state
        ItemChecklistItem item = entry.itemChecklistItem;
        if (item.checkedDateTime == null) {
            item.checkedDateTime = new Date();
        } else {
            item.checkedDateTime = null;
        }

        Activity activity = mNavigator.getActivity();
        if (activity == null) return;

        mRxDisposer.add("onEntryCheckClicked",
                mUpdateItemChecklistItemCmd.execute(item)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((updatedItem, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            }
                        }));
    }

    private Long getChecklistId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.checklistId;
        }
        return null;
    }

    public static class Args implements Serializable {
        public static Args with(long checklistId) {
            Args args = new Args();
            args.checklistId = checklistId;
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

        private Long checklistId;
    }
}
