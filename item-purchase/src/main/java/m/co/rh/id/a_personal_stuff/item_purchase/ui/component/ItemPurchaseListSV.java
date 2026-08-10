package m.co.rh.id.a_personal_stuff.item_purchase.ui.component;

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
import m.co.rh.id.a_personal_stuff.item_purchase.R;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.DeleteItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.PagedItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.QueryItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_purchase.ui.page.ItemPurchaseDetailPage;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class ItemPurchaseListSV extends StatefulView<Activity> implements RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener, ItemPurchaseItemSV.OnItemPurchaseEditClicked, ItemPurchaseItemSV.OnItemPurchaseDeleteClicked {
    private static final String TAG = ItemPurchaseListSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient RxDisposer mRxDisposer;
    private transient ItemPurchaseChangeNotifier mItemPurchaseChangeNotifier;
    private transient QueryItemPurchaseCmd mQueryItemPurchaseCmd;
    private transient DeleteItemPurchaseCmd mDeleteItemPurchaseCmd;
    private transient PagedItemPurchaseCmd mPagedItemPurchaseCmd;

    private long mItemId;
    private SerialBehaviorSubject<String> mSearchString;
    private transient TextWatcher mSearchTextWatcher;
    private transient ItemPurchaseRecyclerViewAdapter mItemPurchaseRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mItemsOnScrollListener;

    public ItemPurchaseListSV(long itemId) {
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
        mItemPurchaseChangeNotifier = mSvProvider.get(ItemPurchaseChangeNotifier.class);
        mQueryItemPurchaseCmd = mSvProvider.get(QueryItemPurchaseCmd.class);
        mDeleteItemPurchaseCmd = mSvProvider.get(DeleteItemPurchaseCmd.class);
        mPagedItemPurchaseCmd = mSvProvider.get(PagedItemPurchaseCmd.class);
        mPagedItemPurchaseCmd.setItemId(mItemId);
        mPagedItemPurchaseCmd.refresh();
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
        mItemPurchaseRecyclerViewAdapter = new ItemPurchaseRecyclerViewAdapter(mPagedItemPurchaseCmd, this, this, mNavigator, this);
        SettingsSharedPreferences settings = mSvProvider.get(SettingsSharedPreferences.class);
        mRxDisposer.add("provideComponent_itemViewModeChanged",
                settings.getItemViewModeFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mode ->
                                mItemPurchaseRecyclerViewAdapter.setCompact(
                                        mode == SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT)));
        mItemsOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mPagedItemPurchaseCmd.loadNextPage();
                }
            }
        };
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.list_item_purchase, container, false);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mItemPurchaseRecyclerViewAdapter);
        recyclerView.addOnScrollListener(mItemsOnScrollListener);
        EditText searchText = rootLayout.findViewById(R.id.edit_text_search);
        searchText.addTextChangedListener(mSearchTextWatcher);

        Context context = activity.getApplicationContext();
        mRxDisposer.add("createView_onSearch",
                mSearchString.getSubject().debounce(700, TimeUnit.MILLISECONDS)
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(mPagedItemPurchaseCmd::search));
        mRxDisposer.add("createView_onItemOnLoading",
                mPagedItemPurchaseCmd.getLoadingFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(swipeRefreshLayout::setRefreshing));
        mRxDisposer.add("createView_onItemPurchasesChanged",
                mPagedItemPurchaseCmd.getItemPurchasesFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemPurchaseStates -> mItemPurchaseRecyclerViewAdapter.notifyItemRefreshed()));
        mRxDisposer.add("createView_onItemPurchaseStateAdded",
                mItemPurchaseChangeNotifier.getAddedItemPurchaseFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemPurchaseRecyclerViewAdapter::notifyItemAdded));
        mRxDisposer.add("createView_onItemPurchaseStateUpdated",
                mItemPurchaseChangeNotifier.getUpdatedItemPurchaseFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemPurchaseRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer.add("createView_onItemPurchaseStateDeleted",
                mItemPurchaseChangeNotifier.getDeletedItemPurchaseFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemPurchaseRecyclerViewAdapter::notifyItemDeleted));
        mRxDisposer.add("createView_onItemPurchaseImageAdded",
                mItemPurchaseChangeNotifier.getAddedItemPurchaseImageFlow()
                        .map(itemPurchaseImage ->
                                mQueryItemPurchaseCmd
                                        .findItemPurchaseStateById(itemPurchaseImage.itemPurchaseId)
                                        .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemPurchaseRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer.add("createView_onItemPurchaseImageDeleted",
                mItemPurchaseChangeNotifier.getDeletedItemPurchaseImageFlow()
                        .map(itemPurchaseImage ->
                                mQueryItemPurchaseCmd
                                        .findItemPurchaseStateById(itemPurchaseImage.itemPurchaseId)
                                        .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemPurchaseRecyclerViewAdapter::notifyItemUpdated));
        return rootLayout;
    }

    @Override
    public void onRefresh() {
        mPagedItemPurchaseCmd.refresh();
    }

    @Override
    public void itemPurchaseItemSV_onItemPurchaseEditClicked(ItemPurchaseState itemPurchaseState) {
        if (itemPurchaseState != null) {
            mNavigator.push(Routes.ITEM_PURCHASE_DETAIL_PAGE,
                    ItemPurchaseDetailPage.Args.with(itemPurchaseState.clone()));
        }
    }

    @Override
    public void itemPurchaseItemSV_onItemPurchaseDeleteClicked(ItemPurchaseState itemPurchaseState) {
        Context context = mSvProvider.getContext();
        String title = context.getString(R.string.title_confirm_delete);
        String message = context.getString(R.string.confirm_delete_, itemPurchaseState.getItemPurchaseDescription());
        mNavigator.push(mNavExtDialogConfig.route_confirmDialog(),
                mNavExtDialogConfig.args_confirmDialog(title, message),
                (navigator, navRoute, activity, currentView) -> confirmDeleteItem(navRoute, itemPurchaseState));
    }

    private void confirmDeleteItem(NavRoute navRoute, ItemPurchaseState itemPurchaseState) {
        Boolean isDelete = mNavExtDialogConfig.result_confirmDialog(navRoute);
        if (isDelete != null && isDelete) {
            Context context = mSvProvider.getContext();
            mRxDisposer.add("confirmDeleteItem",
                    mDeleteItemPurchaseCmd.execute(itemPurchaseState)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((deletedItemState, throwable) -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, cause.getMessage(), cause);
                                } else {
                                    mLogger.i(TAG, context.getString(R.string.success_delete_item_purchase));
                                    mItemPurchaseRecyclerViewAdapter.notifyItemDeleted(deletedItemState);
                                }
                            }));
        }
    }
}
