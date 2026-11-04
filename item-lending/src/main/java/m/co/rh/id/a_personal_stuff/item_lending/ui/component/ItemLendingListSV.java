package m.co.rh.id.a_personal_stuff.item_lending.ui.component;

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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.item_lending.R;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.DeleteItemLendingCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.PagedItemLendingCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.QueryItemLendingCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.notifier.ItemLendingChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_lending.ui.page.ItemLendingDetailPage;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class ItemLendingListSV extends StatefulView<Activity> implements RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener, ItemLendingItemSV.OnItemLendingEditClicked, ItemLendingItemSV.OnItemLendingDeleteClicked {
    private static final String TAG = ItemLendingListSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient RxDisposer mRxDisposer;
    private transient ItemLendingChangeNotifier mItemLendingChangeNotifier;
    private transient QueryItemLendingCmd mQueryItemLendingCmd;
    private transient DeleteItemLendingCmd mDeleteItemLendingCmd;
    private transient PagedItemLendingCmd mPagedItemLendingCmd;

    private long mItemId;
    private SerialBehaviorSubject<String> mSearchString;
    private transient TextWatcher mSearchTextWatcher;
    private transient ItemLendingRecyclerViewAdapter mItemLendingRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mItemsOnScrollListener;

    public ItemLendingListSV(long itemId) {
        mItemId = itemId;
        mSearchString = new SerialBehaviorSubject<>();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mLogger = mSvProvider.get(ILogger.class);
        mNavExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mItemLendingChangeNotifier = mSvProvider.get(ItemLendingChangeNotifier.class);
        mQueryItemLendingCmd = mSvProvider.get(QueryItemLendingCmd.class);
        mDeleteItemLendingCmd = mSvProvider.get(DeleteItemLendingCmd.class);
        mPagedItemLendingCmd = mSvProvider.get(PagedItemLendingCmd.class);
        mPagedItemLendingCmd.setItemId(mItemId);
        mPagedItemLendingCmd.refresh();
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
        mItemLendingRecyclerViewAdapter = new ItemLendingRecyclerViewAdapter(mPagedItemLendingCmd, this, this, mNavigator, this);
        SettingsSharedPreferences settings = mSvProvider.get(SettingsSharedPreferences.class);
        mRxDisposer.add("provideComponent_itemViewModeChanged",
                settings.getItemViewModeFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mode ->
                                mItemLendingRecyclerViewAdapter.setCompact(
                                        mode == SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT)));
        mItemsOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mPagedItemLendingCmd.loadNextPage();
                }
            }
        };
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.list_item_lending, container, false);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mItemLendingRecyclerViewAdapter);
        recyclerView.addOnScrollListener(mItemsOnScrollListener);
        EditText searchEditText = rootLayout.findViewById(R.id.edit_text_search);
        searchEditText.addTextChangedListener(mSearchTextWatcher);
        mRxDisposer.add("createView_onSearch",
                mSearchString.getSubject().debounce(700, TimeUnit.MILLISECONDS)
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(mPagedItemLendingCmd::search));
        mRxDisposer.add("createView_onItemOnLoading",
                mPagedItemLendingCmd.getLoadingFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(swipeRefreshLayout::setRefreshing));
        mRxDisposer.add("createView_onItemLendingsChanged",
                mPagedItemLendingCmd.getItemLendingsFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemLendingStates -> mItemLendingRecyclerViewAdapter.notifyItemRefreshed()));
        mRxDisposer.add("createView_onItemLendingStateAdded",
                mItemLendingChangeNotifier.getAddedItemLendingFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemLendingRecyclerViewAdapter::notifyItemAdded));
        mRxDisposer.add("createView_onItemLendingStateUpdated",
                mItemLendingChangeNotifier.getUpdatedItemLendingFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemLendingRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer.add("createView_onItemLendingStateDeleted",
                mItemLendingChangeNotifier.getDeletedItemLendingFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemLendingRecyclerViewAdapter::notifyItemDeleted));
        mRxDisposer.add("createView_onItemLendingImageChanged",
                mItemLendingChangeNotifier.getAnyItemLendingImageChangeFlow()
                        .map(itemLendingId ->
                                mQueryItemLendingCmd.findItemLendingStateById(itemLendingId).blockingGet())
                        .filter(itemLendingState -> itemLendingState != null)
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemLendingRecyclerViewAdapter::notifyItemUpdated));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mItemLendingRecyclerViewAdapter != null) {
            mItemLendingRecyclerViewAdapter.dispose(activity);
            mItemLendingRecyclerViewAdapter = null;
        }
    }

    @Override
    public void onRefresh() {
        mPagedItemLendingCmd.refresh();
    }

    @Override
    public void itemLendingItemSV_onItemLendingEditClicked(ItemLendingState itemLendingState) {
        mNavigator.push(Routes.ITEM_LENDING_DETAIL_PAGE,
                ItemLendingDetailPage.Args.with(itemLendingState.clone()));
    }

    @Override
    public void itemLendingItemSV_onItemLendingDeleteClicked(ItemLendingState itemLendingState) {
        Context context = mSvProvider.getContext();
        String title = context.getString(R.string.title_confirm_delete);
        String message = context.getString(R.string.confirm_delete_, itemLendingState.getItemLendingBorrowerName());
        mNavigator.push(mNavExtDialogConfig.route_confirmDialog(),
                mNavExtDialogConfig.args_confirmDialog(title, message),
                (navigator, navRoute, activity, currentView) -> confirmDeleteItem(navRoute, itemLendingState));
    }

    private void confirmDeleteItem(NavRoute navRoute, ItemLendingState itemLendingState) {
        Boolean isDelete = mNavExtDialogConfig.result_confirmDialog(navRoute);
        if (isDelete != null && isDelete) {
            Context context = mSvProvider.getContext();
            mRxDisposer.add("confirmDeleteItem",
                    mDeleteItemLendingCmd.execute(itemLendingState)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((deletedItemState, throwable) -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, cause.getMessage(), cause);
                                } else {
                                    mLogger.i(TAG, context.getString(R.string.success_delete_item_lending));
                                    mItemLendingRecyclerViewAdapter.notifyItemDeleted(deletedItemState);
                                }
                            }));
        }
    }
}
