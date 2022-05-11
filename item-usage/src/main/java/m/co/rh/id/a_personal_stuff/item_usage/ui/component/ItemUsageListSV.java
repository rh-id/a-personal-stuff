package m.co.rh.id.a_personal_stuff.item_usage.ui.component;

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
import m.co.rh.id.a_personal_stuff.item_usage.R;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.DeleteItemUsageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.PagedItemUsageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.QueryItemUsageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_usage.ui.page.ItemUsageDetailPage;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class ItemUsageListSV extends StatefulView<Activity> implements RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener, ItemUsageItemSV.OnItemUsageEditClicked, ItemUsageItemSV.OnItemUsageDeleteClicked {
    private static final String TAG = ItemUsageListSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient RxDisposer mRxDisposer;
    private transient ItemUsageChangeNotifier mItemUsageChangeNotifier;
    private transient QueryItemUsageCmd mQueryItemUsageCmd;
    private transient DeleteItemUsageCmd mDeleteItemUsageCmd;
    private transient PagedItemUsageCmd mPagedItemUsageCmd;

    private long mItemId;
    private SerialBehaviorSubject<String> mSearchString;
    private transient TextWatcher mSearchTextWatcher;
    private transient ItemUsageRecyclerViewAdapter mItemUsageRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mItemsOnScrollListener;

    public ItemUsageListSV(long itemId) {
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
        mItemUsageChangeNotifier = mSvProvider.get(ItemUsageChangeNotifier.class);
        mQueryItemUsageCmd = mSvProvider.get(QueryItemUsageCmd.class);
        mDeleteItemUsageCmd = mSvProvider.get(DeleteItemUsageCmd.class);
        mPagedItemUsageCmd = mSvProvider.get(PagedItemUsageCmd.class);
        mPagedItemUsageCmd.setItemId(mItemId);
        mPagedItemUsageCmd.refresh();
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
        mItemUsageRecyclerViewAdapter = new ItemUsageRecyclerViewAdapter(mPagedItemUsageCmd, this, this, mNavigator, this);
        mItemsOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mPagedItemUsageCmd.loadNextPage();
                }
            }
        };
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.list_item_usage, container, false);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mItemUsageRecyclerViewAdapter);
        recyclerView.addOnScrollListener(mItemsOnScrollListener);
        EditText searchEditText = rootLayout.findViewById(R.id.edit_text_search);
        searchEditText.addTextChangedListener(mSearchTextWatcher);
        mRxDisposer.add("createView_onSearch",
                mSearchString.getSubject().debounce(700, TimeUnit.MILLISECONDS)
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(mPagedItemUsageCmd::search));
        mRxDisposer.add("createView_onItemOnLoading",
                mPagedItemUsageCmd.getLoadingFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(swipeRefreshLayout::setRefreshing));
        mRxDisposer.add("createView_onItemUsagesChanged",
                mPagedItemUsageCmd.getItemUsagesFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemUsageStates -> mItemUsageRecyclerViewAdapter.notifyItemRefreshed()));
        mRxDisposer.add("createView_onItemUsageStateAdded",
                mItemUsageChangeNotifier.getAddedItemUsageFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemUsageRecyclerViewAdapter::notifyItemAdded));
        mRxDisposer.add("createView_onItemUsageStateUpdated",
                mItemUsageChangeNotifier.getUpdatedItemUsageFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemUsageRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer.add("createView_onItemUsageStateDeleted",
                mItemUsageChangeNotifier.getDeletedItemUsageFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemUsageRecyclerViewAdapter::notifyItemDeleted));
        mRxDisposer.add("createView_onItemUsageImageAdded",
                mItemUsageChangeNotifier.getAddedItemUsageImageFlow()
                        .map(itemUsageImage ->
                                mQueryItemUsageCmd
                                        .findItemUsageStateById(itemUsageImage.itemUsageId)
                                        .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemUsageRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer.add("createView_onItemUsageImageDeleted",
                mItemUsageChangeNotifier.getDeletedItemUsageImageFlow()
                        .map(itemUsageImage ->
                                mQueryItemUsageCmd
                                        .findItemUsageStateById(itemUsageImage.itemUsageId)
                                        .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemUsageRecyclerViewAdapter::notifyItemUpdated));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mItemUsageRecyclerViewAdapter != null) {
            mItemUsageRecyclerViewAdapter.dispose(activity);
            mItemUsageRecyclerViewAdapter = null;
        }
    }

    @Override
    public void onRefresh() {
        mPagedItemUsageCmd.refresh();
    }

    @Override
    public void itemUsageItemSV_onItemUsageEditClicked(ItemUsageState itemUsageState) {
        mNavigator.push(Routes.ITEM_USAGE_DETAIL_PAGE,
                ItemUsageDetailPage.Args.with(itemUsageState.clone()));
    }

    @Override
    public void itemUsageItemSV_onItemUsageDeleteClicked(ItemUsageState itemUsageState) {
        Context context = mSvProvider.getContext();
        String title = context.getString(R.string.title_confirm_delete);
        String message = context.getString(R.string.confirm_delete_, itemUsageState.getItemUsageDescription());
        mNavigator.push(mNavExtDialogConfig.route_confirmDialog(),
                mNavExtDialogConfig.args_confirmDialog(title, message),
                (navigator, navRoute, activity, currentView) -> confirmDeleteItem(navRoute, itemUsageState));
    }

    private void confirmDeleteItem(NavRoute navRoute, ItemUsageState itemUsageState) {
        Boolean isDelete = mNavExtDialogConfig.result_confirmDialog(navRoute);
        if (isDelete != null && isDelete) {
            Context context = mSvProvider.getContext();
            mRxDisposer.add("confirmDeleteItem",
                    mDeleteItemUsageCmd.execute(itemUsageState)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((deletedItemState, throwable) -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, cause.getMessage(), cause);
                                } else {
                                    mLogger.i(TAG, context.getString(R.string.success_delete_item_usage));
                                    mItemUsageRecyclerViewAdapter.notifyItemDeleted(deletedItemState);
                                }
                            }));
        }
    }
}
