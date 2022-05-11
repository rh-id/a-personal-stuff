package m.co.rh.id.a_personal_stuff.item_maintenance.ui.component;

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
import m.co.rh.id.a_personal_stuff.item_maintenance.R;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.DeleteItemMaintenanceCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.PagedItemMaintenanceCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.QueryItemMaintenanceCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.notifier.ItemMaintenanceChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_maintenance.ui.page.ItemMaintenanceDetailPage;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class ItemMaintenanceListSV extends StatefulView<Activity> implements RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener, ItemMaintenanceItemSV.OnItemMaintenanceEditClicked, ItemMaintenanceItemSV.OnItemMaintenanceDeleteClicked {
    private static final String TAG = ItemMaintenanceListSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient RxDisposer mRxDisposer;
    private transient ItemMaintenanceChangeNotifier mItemMaintenanceChangeNotifier;
    private transient QueryItemMaintenanceCmd mQueryItemMaintenanceCmd;
    private transient DeleteItemMaintenanceCmd mDeleteItemMaintenanceCmd;
    private transient PagedItemMaintenanceCmd mPagedItemMaintenanceCmd;

    private long mItemId;
    private SerialBehaviorSubject<String> mSearchString;
    private transient TextWatcher mSearchTextWatcher;
    private transient ItemMaintenanceRecyclerViewAdapter mItemMaintenanceRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mItemsOnScrollListener;

    public ItemMaintenanceListSV(long itemId) {
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
        mItemMaintenanceChangeNotifier = mSvProvider.get(ItemMaintenanceChangeNotifier.class);
        mQueryItemMaintenanceCmd = mSvProvider.get(QueryItemMaintenanceCmd.class);
        mDeleteItemMaintenanceCmd = mSvProvider.get(DeleteItemMaintenanceCmd.class);
        mPagedItemMaintenanceCmd = mSvProvider.get(PagedItemMaintenanceCmd.class);
        mPagedItemMaintenanceCmd.setItemId(mItemId);
        mPagedItemMaintenanceCmd.refresh();
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
        mItemMaintenanceRecyclerViewAdapter = new ItemMaintenanceRecyclerViewAdapter(mPagedItemMaintenanceCmd, this, this, mNavigator, this);
        mItemsOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mPagedItemMaintenanceCmd.loadNextPage();
                }
            }
        };
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.list_item_maintenance, container, false);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mItemMaintenanceRecyclerViewAdapter);
        recyclerView.addOnScrollListener(mItemsOnScrollListener);
        EditText searchEditText = rootLayout.findViewById(R.id.edit_text_search);
        searchEditText.addTextChangedListener(mSearchTextWatcher);
        mRxDisposer.add("createView_onSearch",
                mSearchString.getSubject().debounce(700, TimeUnit.MILLISECONDS)
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(mPagedItemMaintenanceCmd::search));
        mRxDisposer.add("createView_onItemOnLoading",
                mPagedItemMaintenanceCmd.getLoadingFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(swipeRefreshLayout::setRefreshing));
        mRxDisposer.add("createView_onItemMaintenancesChanged",
                mPagedItemMaintenanceCmd.getItemMaintenanceStatesFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemMaintenanceStates -> mItemMaintenanceRecyclerViewAdapter.notifyItemRefreshed()));
        mRxDisposer.add("createView_onItemMaintenanceStateAdded",
                mItemMaintenanceChangeNotifier.getAddedItemMaintenanceFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemMaintenanceRecyclerViewAdapter::notifyItemAdded));
        mRxDisposer.add("createView_onItemMaintenanceStateUpdated",
                mItemMaintenanceChangeNotifier.getUpdatedItemMaintenanceFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemMaintenanceRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer.add("createView_onItemMaintenanceStateDeleted",
                mItemMaintenanceChangeNotifier.getDeletedItemMaintenanceFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemMaintenanceRecyclerViewAdapter::notifyItemDeleted));
        mRxDisposer.add("createView_onItemMaintenanceImageAdded",
                mItemMaintenanceChangeNotifier.getAddedImageFlow()
                        .map(itemMaintenanceImage ->
                                mQueryItemMaintenanceCmd
                                        .findItemMaintenanceStateById(itemMaintenanceImage.itemMaintenanceId)
                                        .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemMaintenanceRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer.add("createView_onItemMaintenanceImageDeleted",
                mItemMaintenanceChangeNotifier.getDeletedImageFlow()
                        .map(itemMaintenanceImage ->
                                mQueryItemMaintenanceCmd
                                        .findItemMaintenanceStateById(itemMaintenanceImage.itemMaintenanceId)
                                        .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemMaintenanceRecyclerViewAdapter::notifyItemUpdated));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mItemMaintenanceRecyclerViewAdapter != null) {
            mItemMaintenanceRecyclerViewAdapter.dispose(activity);
            mItemMaintenanceRecyclerViewAdapter = null;
        }
    }

    @Override
    public void onRefresh() {
        mPagedItemMaintenanceCmd.refresh();
    }

    @Override
    public void itemMaintenanceItemSV_onItemMaintenanceEditClicked(ItemMaintenanceState itemMaintenanceState) {
        mNavigator.push(Routes.ITEM_MAINTENANCE_DETAIL_PAGE,
                ItemMaintenanceDetailPage.Args.with(itemMaintenanceState.clone()));
    }

    @Override
    public void itemMaintenanceItemSV_onItemMaintenanceDeleteClicked(ItemMaintenanceState itemMaintenanceState) {
        Context context = mSvProvider.getContext();
        String title = context.getString(R.string.title_confirm_delete);
        String message = context.getString(R.string.confirm_delete_, itemMaintenanceState.getItemMaintenanceDescription());
        mNavigator.push(mNavExtDialogConfig.route_confirmDialog(),
                mNavExtDialogConfig.args_confirmDialog(title, message),
                (navigator, navRoute, activity, currentView) -> confirmDeleteItem(navRoute, itemMaintenanceState));
    }

    private void confirmDeleteItem(NavRoute navRoute, ItemMaintenanceState itemMaintenanceState) {
        Boolean isDelete = mNavExtDialogConfig.result_confirmDialog(navRoute);
        if (isDelete != null && isDelete) {
            Context context = mSvProvider.getContext();
            mRxDisposer.add("confirmDeleteItem",
                    mDeleteItemMaintenanceCmd.execute(itemMaintenanceState)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((deletedItemMaintenanceState, throwable) -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, cause.getMessage(), cause);
                                } else {
                                    mLogger.i(TAG, context.getString(R.string.success_delete_item_maintenance));
                                    mItemMaintenanceRecyclerViewAdapter.notifyItemDeleted(deletedItemMaintenanceState);
                                }
                            }));
        }
    }
}
