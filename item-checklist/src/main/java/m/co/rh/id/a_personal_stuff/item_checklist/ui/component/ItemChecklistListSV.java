package m.co.rh.id.a_personal_stuff.item_checklist.ui.component;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.item_checklist.R;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.DeleteItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.PagedItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.QueryItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier.ItemChecklistChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_checklist.ui.page.ItemChecklistDetailPage;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class ItemChecklistListSV extends StatefulView<Activity> implements RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener, ItemChecklistItemSV.OnItemChecklistEditClicked, ItemChecklistItemSV.OnItemChecklistDeleteClicked {
    private static final String TAG = ItemChecklistListSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient RxDisposer mRxDisposer;
    private transient ItemChecklistChangeNotifier mItemChecklistChangeNotifier;
    private transient QueryItemChecklistCmd mQueryItemChecklistCmd;
    private transient DeleteItemChecklistCmd mDeleteItemChecklistCmd;
    private transient PagedItemChecklistCmd mPagedItemChecklistCmd;

    private SerialBehaviorSubject<String> mSearchString;
    private final Long mFilterItemId;
    private transient TextWatcher mSearchTextWatcher;
    private transient ItemChecklistRecyclerViewAdapter mItemChecklistRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mItemsOnScrollListener;
    private transient SwipeRefreshLayout mSwipeRefreshLayout;

    public ItemChecklistListSV() {
        this(null);
    }

    public ItemChecklistListSV(Long filterItemId) {
        mSearchString = new SerialBehaviorSubject<>();
        mFilterItemId = filterItemId;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mLogger = mSvProvider.get(ILogger.class);
        mNavExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mItemChecklistChangeNotifier = mSvProvider.get(ItemChecklistChangeNotifier.class);
        mQueryItemChecklistCmd = mSvProvider.get(QueryItemChecklistCmd.class);
        mDeleteItemChecklistCmd = mSvProvider.get(DeleteItemChecklistCmd.class);
        if (mFilterItemId == null) {
            mPagedItemChecklistCmd = mSvProvider.get(PagedItemChecklistCmd.class);
            mPagedItemChecklistCmd.refresh();
        }
        if (mFilterItemId == null) {
            mSearchTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // leave blank
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // leave blank
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    mSearchString.onNext(editable.toString());
                }
            };
        }
        mItemChecklistRecyclerViewAdapter = new ItemChecklistRecyclerViewAdapter(this, this, mNavigator, this);
        SettingsSharedPreferences settings = mSvProvider.get(SettingsSharedPreferences.class);
        mRxDisposer.add("provideComponent_itemViewModeChanged",
                settings.getItemViewModeFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mode ->
                                mItemChecklistRecyclerViewAdapter.setCompact(
                                        mode == SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT)));
        if (mFilterItemId == null) {
            mItemsOnScrollListener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                        mPagedItemChecklistCmd.loadNextPage();
                    }
                }
            };
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.list_item_checklist, container, false);
        mSwipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mItemChecklistRecyclerViewAdapter);
        if (mFilterItemId == null) {
            recyclerView.addOnScrollListener(mItemsOnScrollListener);
        }
        if (mFilterItemId == null) {
            EditText searchEditText = rootLayout.findViewById(R.id.edit_text_search);
            searchEditText.addTextChangedListener(mSearchTextWatcher);
        } else {
            rootLayout.findViewById(R.id.container_search).setVisibility(View.GONE);
        }
        if (mFilterItemId == null) {
            mRxDisposer.add("createView_onSearch",
                    mSearchString.getSubject().debounce(700, TimeUnit.MILLISECONDS)
                            .observeOn(Schedulers.from(mExecutorService))
                            .subscribe(mPagedItemChecklistCmd::search));
            mRxDisposer.add("createView_onItemOnLoading",
                    mPagedItemChecklistCmd.getLoadingFlow().observeOn(AndroidSchedulers.mainThread())
                            .subscribe(mSwipeRefreshLayout::setRefreshing));
            mRxDisposer.add("createView_onItemChecklistsChanged",
                    mPagedItemChecklistCmd.getItemChecklistsFlow().observeOn(AndroidSchedulers.mainThread())
                            .subscribe(itemChecklistStates -> {
                                mItemChecklistRecyclerViewAdapter.setItems(itemChecklistStates);
                                loadProgress();
                            }));
            mRxDisposer.add("createView_onItemChecklistStateAdded",
                    mItemChecklistChangeNotifier.getChecklistAddedFlow().observeOn(AndroidSchedulers.mainThread())
                            .subscribe(itemChecklistState -> {
                                mItemChecklistRecyclerViewAdapter.notifyItemAdded(itemChecklistState);
                                loadProgress();
                            }));
            mRxDisposer.add("createView_onItemChecklistStateUpdated",
                    mItemChecklistChangeNotifier.getChecklistUpdatedFlow().observeOn(AndroidSchedulers.mainThread())
                            .subscribe(itemChecklistState -> {
                                mItemChecklistRecyclerViewAdapter.notifyItemUpdated(itemChecklistState);
                                loadProgress();
                            }));
            mRxDisposer.add("createView_onItemChecklistStateDeleted",
                    mItemChecklistChangeNotifier.getChecklistDeletedFlow().observeOn(AndroidSchedulers.mainThread())
                            .subscribe(itemChecklistState -> {
                                mItemChecklistRecyclerViewAdapter.notifyItemDeleted(itemChecklistState);
                                loadProgress();
                            }));
            mRxDisposer.add("createView_onItemChecklistItemChanged",
                    mItemChecklistChangeNotifier.getAnyItemChecklistItemChangeFlow()
                            .flatMap(checklistId -> mQueryItemChecklistCmd
                                    .findItemChecklistStateById(checklistId)
                                    .toFlowable())
                            .filter(Objects::nonNull)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(itemChecklistState -> {
                                mItemChecklistRecyclerViewAdapter.notifyItemUpdated(itemChecklistState);
                                loadProgress();
                            }));
        } else {
            mRxDisposer.add("createView_onAnyChecklistChanged_reload",
                    Flowable.merge(mItemChecklistChangeNotifier.getAnyItemChecklistChangeFlow(),
                            mItemChecklistChangeNotifier.getAnyItemChecklistItemChangeFlow())
                            .subscribe(ignored -> reload()));
            reload();
        }
        return rootLayout;
    }

    private void loadProgress() {
        mRxDisposer.add("loadProgress",
                mQueryItemChecklistCmd.findAllProgress()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(progressList -> {
                            mItemChecklistRecyclerViewAdapter.setProgressMap(progressList);
                            mItemChecklistRecyclerViewAdapter.notifyItemRangeChanged(0, mItemChecklistRecyclerViewAdapter.getItemCount());
                        }));
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mItemChecklistRecyclerViewAdapter != null) {
            mItemChecklistRecyclerViewAdapter.dispose(activity);
            mItemChecklistRecyclerViewAdapter = null;
        }
    }

    @Override
    public void onRefresh() {
        if (mFilterItemId != null) {
            if (mSwipeRefreshLayout != null) {
                mSwipeRefreshLayout.setRefreshing(true);
            }
            reload();
        } else {
            mPagedItemChecklistCmd.refresh();
        }
    }

    private void reload() {
        mRxDisposer.add("reload",
                mQueryItemChecklistCmd.findChecklistStatesByItemId(mFilterItemId)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((itemChecklistStates, throwable) -> {
                            if (mSwipeRefreshLayout != null) {
                                mSwipeRefreshLayout.setRefreshing(false);
                            }
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else {
                                mItemChecklistRecyclerViewAdapter.setItems(itemChecklistStates);
                                loadProgress();
                            }
                        }));
    }

    @Override
    public void itemChecklistItemSV_onItemChecklistEditClicked(ItemChecklistState itemChecklistState) {
        mNavigator.push(Routes.ITEM_CHECKLIST_DETAIL_PAGE,
                ItemChecklistDetailPage.Args.with(itemChecklistState.getChecklistId()));
    }

    @Override
    public void itemChecklistItemSV_onItemChecklistDeleteClicked(ItemChecklistState itemChecklistState) {
        Context context = mSvProvider.getContext();
        String title = context.getString(R.string.title_confirm_delete);
        String message = context.getString(R.string.confirm_delete_, itemChecklistState.getTitle());
        mNavigator.push(mNavExtDialogConfig.route_confirmDialog(),
                mNavExtDialogConfig.args_confirmDialog(title, message),
                (navigator, navRoute, activity, currentView) -> confirmDeleteItem(navRoute, itemChecklistState));
    }

    private void confirmDeleteItem(NavRoute navRoute, ItemChecklistState itemChecklistState) {
        Boolean isDelete = mNavExtDialogConfig.result_confirmDialog(navRoute);
        if (isDelete != null && isDelete) {
            Context context = mSvProvider.getContext();
            mRxDisposer.add("confirmDeleteItem",
                    mDeleteItemChecklistCmd.execute(itemChecklistState)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((deletedItemState, throwable) -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, cause.getMessage(), cause);
                                } else {
                                    mLogger.i(TAG, context.getString(R.string.success_delete_checklist_,
                                            itemChecklistState.getTitle()));
                                    mItemChecklistRecyclerViewAdapter.notifyItemDeleted(deletedItemState);
                                }
                            }));
        }
    }
}
