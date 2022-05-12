package m.co.rh.id.a_personal_stuff.app.ui.component.item;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.command.DeleteItemCmd;
import m.co.rh.id.a_personal_stuff.app.provider.command.PagedItemCmd;
import m.co.rh.id.a_personal_stuff.app.provider.command.QueryItemCmd;
import m.co.rh.id.a_personal_stuff.app.ui.page.ItemDetailPage;
import m.co.rh.id.a_personal_stuff.barcode.ui.NavBarcodeConfig;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class ItemListSV extends StatefulView<Activity> implements RequireComponent<Provider>, ItemItemSV.OnItemEditClicked, ItemItemSV.OnItemDeleteClicked, SwipeRefreshLayout.OnRefreshListener, View.OnClickListener {

    private static final String TAG = ItemListSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient NavBarcodeConfig mNavBarcodeConfig;
    private transient RxDisposer mRxDisposer;
    private transient ItemChangeNotifier mItemChangeNotifier;
    private transient ItemUsageChangeNotifier mItemUsageChangeNotifier;
    private transient PagedItemCmd mPagedItemCmd;
    private transient DeleteItemCmd mDeleteItemCmd;
    private transient QueryItemCmd mQueryItemCmd;

    private transient ItemAdapter mItemRecyclerViewAdapter;
    private transient TextWatcher mSearchTextWatcher;
    private transient RecyclerView.OnScrollListener mItemsOnScrollListener;

    private boolean mSelectMode;
    private SerialBehaviorSubject<String> mSearchString;
    private SerialBehaviorSubject<Long> mShowItemId;

    public ItemListSV() {
        this(false);
    }

    public ItemListSV(boolean selectMode) {
        mSelectMode = selectMode;
        mSearchString = new SerialBehaviorSubject<>();
        mShowItemId = new SerialBehaviorSubject<>();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mLogger = mSvProvider.get(ILogger.class);
        mNavExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
        mNavBarcodeConfig = mSvProvider.get(NavBarcodeConfig.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mItemChangeNotifier = mSvProvider.get(ItemChangeNotifier.class);
        mItemUsageChangeNotifier = mSvProvider.get(ItemUsageChangeNotifier.class);
        mPagedItemCmd = mSvProvider.get(PagedItemCmd.class);
        mPagedItemCmd.refresh();
        mDeleteItemCmd = mSvProvider.get(DeleteItemCmd.class);
        mQueryItemCmd = mSvProvider.get(QueryItemCmd.class);
        if (mSelectMode) {
            mItemRecyclerViewAdapter = new SelectableItemRecyclerViewAdapter(mPagedItemCmd, mNavigator, this);
        } else {
            mItemRecyclerViewAdapter = new ItemRecyclerViewAdapter(mPagedItemCmd, this, this, mNavigator, this);
        }
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
        mItemsOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mPagedItemCmd.loadNextPage();
                }
            }
        };
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.list_item, container, false);
        EditText searchEditText = rootLayout.findViewById(R.id.edit_text_search);
        searchEditText.addTextChangedListener(mSearchTextWatcher);
        Button scanButton = rootLayout.findViewById(R.id.button_scan_barcode);
        scanButton.setOnClickListener(this);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mItemRecyclerViewAdapter);
        recyclerView.addOnScrollListener(mItemsOnScrollListener);
        mRxDisposer.add("createView_onShowItemId",
                mShowItemId.getSubject()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(itemId -> mPagedItemCmd.refreshWithItemId(itemId))
        );
        mRxDisposer.add("createView_onSearch",
                mSearchString.getSubject().debounce(700, TimeUnit.MILLISECONDS)
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(search -> mPagedItemCmd.search(search)));
        mRxDisposer
                .add("createView_onItemRefreshed",
                        mPagedItemCmd.getItemsFlow()
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(itemStates ->
                                        mItemRecyclerViewAdapter.notifyItemRefreshed())
                );
        mRxDisposer.add("createView_onItemLoading",
                mPagedItemCmd.getLoadingFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(swipeRefreshLayout::setRefreshing));
        mRxDisposer.add("createView_onItemAdded",
                mItemChangeNotifier.getAddedItemFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemState -> {
                            mItemRecyclerViewAdapter.notifyItemAdded(itemState);
                            recyclerView.scrollToPosition(0);
                        }));
        mRxDisposer.add("createView_onItemUpdated",
                mItemChangeNotifier.getUpdatedItemFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer.add("createView_onItemDeleted",
                mItemChangeNotifier.getDeletedItemFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemRecyclerViewAdapter::notifyItemDeleted));
        mRxDisposer.add("createView_onItemTagDeleted",
                mItemChangeNotifier.getDeletedItemTagFlow()
                        .map(itemTag -> mQueryItemCmd.findItemStateByItemId(itemTag.itemId)
                                .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemState ->
                                mItemRecyclerViewAdapter.notifyItemUpdated(itemState)));
        mRxDisposer.add("createView_onItemTagAdded",
                mItemChangeNotifier.getAddedItemTagFlow()
                        .map(itemTag -> mQueryItemCmd.findItemStateByItemId(itemTag.itemId)
                                .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemState ->
                                mItemRecyclerViewAdapter.notifyItemUpdated(itemState)));
        mRxDisposer.add("createView_onItemImagesDeleted",
                mItemChangeNotifier.getDeletedItemImagesFlow()
                        .map(itemImages -> {
                            List<ItemState> updatedItemState = new ArrayList<>();
                            if (!itemImages.isEmpty()) {
                                Set<Long> itemIds = new LinkedHashSet<>();
                                for (ItemImage itemImage : itemImages) {
                                    itemIds.add(itemImage.itemId);
                                }
                                ArrayList<ItemState> itemStates = mPagedItemCmd.getAllItems();
                                List<Long> updatedItemIds = new ArrayList<>();
                                for (ItemState itemState : itemStates) {
                                    Long itemId = itemState.getItemId();
                                    if (itemIds.contains(itemId)) {
                                        updatedItemIds.add(itemId);
                                    }
                                }
                                updatedItemState = mQueryItemCmd.findItemStateByItemIds(updatedItemIds)
                                        .blockingGet();
                            }
                            return updatedItemState;
                        })
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemStates -> {
                            if (!itemStates.isEmpty()) {
                                for (ItemState itemState : itemStates) {
                                    mItemRecyclerViewAdapter.notifyItemUpdated(itemState);
                                }
                            }
                        }));
        mRxDisposer.add("createView_onItemImagesAdded",
                mItemChangeNotifier.getAddedItemImageFlow()
                        .map(itemImage -> mQueryItemCmd.findItemStateByItemIds(
                                        Collections.singletonList(itemImage.itemId))
                                .blockingGet().get(0))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemRecyclerViewAdapter::notifyItemUpdated));
        mRxDisposer.add("createView_onItemUsageAdded",
                mItemUsageChangeNotifier.getAddedItemUsageFlow()
                        .map(itemUsageState -> mQueryItemCmd
                                .findItemStateByItemId(itemUsageState.getItemId())
                                .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemState -> mItemRecyclerViewAdapter.notifyItemUpdated(itemState)));
        mRxDisposer.add("createView_onItemUsageUpdated",
                mItemUsageChangeNotifier.getUpdatedItemUsageFlow()
                        .map(itemUsageState -> mQueryItemCmd
                                .findItemStateByItemId(itemUsageState.getItemId())
                                .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemState -> mItemRecyclerViewAdapter.notifyItemUpdated(itemState)));
        mRxDisposer.add("createView_onItemUsageDeleted",
                mItemUsageChangeNotifier.getDeletedItemUsageFlow()
                        .map(itemUsageState -> mQueryItemCmd
                                .findItemStateByItemId(itemUsageState.getItemId())
                                .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemState -> mItemRecyclerViewAdapter.notifyItemUpdated(itemState)));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mItemRecyclerViewAdapter != null) {
            mItemRecyclerViewAdapter.dispose(activity);
            mItemRecyclerViewAdapter = null;
        }
    }

    @Override
    public void itemItemSv_onItemEditClicked(ItemState itemState) {
        mNavigator.push(Routes.ITEM_DETAIL_PAGE, ItemDetailPage.Args.forUpdate(itemState.clone()));
    }

    @Override
    public void itemItemSv_onItemDeleteClicked(ItemState itemState) {
        Context context = mSvProvider.getContext();
        String title = context.getString(R.string.title_confirm_delete);
        String message = context.getString(R.string.confirm_delete_, itemState.getItemName());
        mNavigator.push(mNavExtDialogConfig.route_confirmDialog(),
                mNavExtDialogConfig.args_confirmDialog(title, message),
                (navigator, navRoute, activity, currentView) -> confirmDeleteItem(navRoute, itemState));
    }

    public void showItemId(long itemId) {
        mShowItemId.onNext(itemId);
    }

    private void confirmDeleteItem(NavRoute navRoute, ItemState itemState) {
        Boolean isDelete = mNavExtDialogConfig.result_confirmDialog(navRoute);
        if (isDelete != null && isDelete) {
            Context context = mSvProvider.getContext();
            mRxDisposer.add("confirmDeleteItem",
                    mDeleteItemCmd.execute(itemState).observeOn(AndroidSchedulers.mainThread())
                            .subscribe((deletedItemState, throwable) -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, cause.getMessage(), cause);
                                } else {
                                    mLogger.i(TAG, context.getString(R.string.success_delete_item_, deletedItemState.getItemName()));
                                    mItemRecyclerViewAdapter.notifyItemDeleted(deletedItemState);
                                }
                            }));
        }
    }

    @Override
    public void onRefresh() {
        mPagedItemCmd.refresh();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_scan_barcode) {
            mNavigator.push(mNavBarcodeConfig.route_scanBarcodePage(),
                    (navigator, navRoute, activity, currentView) -> updateScanResult(navRoute, currentView));
        }
    }

    public ItemState getSelectedItem() {
        if (mSelectMode) {
            if (mItemRecyclerViewAdapter instanceof SelectableItemRecyclerViewAdapter) {
                return ((SelectableItemRecyclerViewAdapter) mItemRecyclerViewAdapter)
                        .getSelectedItem();
            }
        }
        return null;
    }

    private void updateScanResult(NavRoute navRoute, View currentView) {
        String barcode = mNavBarcodeConfig.result_scanBarcodePage_barcode(navRoute);
        if (barcode != null) {
            EditText searchEditText = currentView.findViewById(R.id.edit_text_search);
            searchEditText.setText(barcode);
        }
    }
}
