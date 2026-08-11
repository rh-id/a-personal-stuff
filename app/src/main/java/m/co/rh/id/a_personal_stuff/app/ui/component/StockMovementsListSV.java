package m.co.rh.id.a_personal_stuff.app.ui.component;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.command.QueryItemCmd;
import m.co.rh.id.a_personal_stuff.app.ui.model.StockMovement;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.DeleteItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.QueryItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier.ItemPurchaseChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_purchase.ui.page.ItemPurchaseDetailPage;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.DeleteItemUsageCmd;
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
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.item_usage.ui.component.ItemUsageItemSV;
import m.co.rh.id.a_personal_stuff.item_purchase.ui.component.ItemPurchaseItemSV;
import m.co.rh.id.aprovider.Provider;

public class StockMovementsListSV extends StatefulView<Activity> implements RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener,
        ItemUsageItemSV.OnItemUsageEditClicked, ItemUsageItemSV.OnItemUsageDeleteClicked,
        ItemPurchaseItemSV.OnItemPurchaseEditClicked, ItemPurchaseItemSV.OnItemPurchaseDeleteClicked {
    private static final String TAG = StockMovementsListSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient RxDisposer mRxDisposer;

    private transient QueryItemCmd mQueryItemCmd;
    private transient QueryItemUsageCmd mQueryItemUsageCmd;
    private transient QueryItemPurchaseCmd mQueryItemPurchaseCmd;
    private transient DeleteItemUsageCmd mDeleteItemUsageCmd;
    private transient DeleteItemPurchaseCmd mDeleteItemPurchaseCmd;
    private transient ItemUsageChangeNotifier mItemUsageChangeNotifier;
    private transient ItemPurchaseChangeNotifier mItemPurchaseChangeNotifier;
    private transient ItemChangeNotifier mItemChangeNotifier;

    private long mItemId;
    private List<StockMovement> mStockMovements;
    private transient ItemState mItemState;
    private transient StockMovementRecyclerViewAdapter mAdapter;
    private transient TextView mSummaryTextView;
    private transient View mView;

    public StockMovementsListSV(long itemId) {
        mItemId = itemId;
        mStockMovements = new ArrayList<>();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mLogger = mSvProvider.get(ILogger.class);
        mNavExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mQueryItemCmd = mSvProvider.get(QueryItemCmd.class);
        mQueryItemUsageCmd = mSvProvider.get(QueryItemUsageCmd.class);
        mQueryItemPurchaseCmd = mSvProvider.get(QueryItemPurchaseCmd.class);
        mDeleteItemUsageCmd = mSvProvider.get(DeleteItemUsageCmd.class);
        mDeleteItemPurchaseCmd = mSvProvider.get(DeleteItemPurchaseCmd.class);
        mItemUsageChangeNotifier = mSvProvider.get(ItemUsageChangeNotifier.class);
        mItemPurchaseChangeNotifier = mSvProvider.get(ItemPurchaseChangeNotifier.class);
        mItemChangeNotifier = mSvProvider.get(ItemChangeNotifier.class);
        m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences settings =
                mSvProvider.get(m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences.class);
        mRxDisposer.add("provideComponent_itemViewModeChanged",
                settings.getItemViewModeFlow()
                        .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                        .subscribe(mode -> {
                            if (mAdapter != null) {
                                mAdapter.setCompact(
                                        mode == m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT);
                            }
                        }));
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.list_item_stock_movements, container, false);
        mView = rootLayout;
        mSummaryTextView = rootLayout.findViewById(R.id.text_summary);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        androidx.recyclerview.widget.RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new m.co.rh.id.a_personal_stuff.base.ui.recyclerview.CustomLinearLayoutManager(activity));
        mAdapter = new StockMovementRecyclerViewAdapter(mNavigator, this, this, this, this, this);
        m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences settings =
                mSvProvider.get(m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences.class);
        mAdapter.setCompact(settings.getItemViewMode()
                == m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT);
        recyclerView.setAdapter(mAdapter);

        swipeRefreshLayout.setRefreshing(true);
        loadMovements(swipeRefreshLayout);

        mRxDisposer.add("createView_onItemUsageChanged",
                mItemUsageChangeNotifier.getAnyItemUsageChangeFlow()
                        .filter(itemId -> itemId != null && itemId.equals(mItemId))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(change -> loadMovements(swipeRefreshLayout)));

        mRxDisposer.add("createView_onItemPurchaseChanged",
                mItemPurchaseChangeNotifier.getAnyItemPurchaseChangeFlow()
                        .filter(itemId -> itemId != null && itemId.equals(mItemId))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(change -> loadMovements(swipeRefreshLayout)));

        // Image-level changes emit the usage/purchase id (not item id), so
        // resolve to the state and filter by this page's item id — matches how
        // the dedicated usage/purchase lists react to image add/delete.
        mRxDisposer.add("createView_onItemUsageImageChanged",
                mItemUsageChangeNotifier.getAnyItemUsageImageChangeFlow()
                        .filter(usageId -> usageId != null)
                        .map(usageId -> mQueryItemUsageCmd.findItemUsageStateById(usageId).blockingGet())
                        .filter(state -> state != null && state.getItemId() != null && state.getItemId().equals(mItemId))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(change -> loadMovements(swipeRefreshLayout)));

        mRxDisposer.add("createView_onItemPurchaseImageChanged",
                mItemPurchaseChangeNotifier.getAnyItemPurchaseImageChangeFlow()
                        .filter(purchaseId -> purchaseId != null)
                        .map(purchaseId -> mQueryItemPurchaseCmd.findItemPurchaseStateById(purchaseId).blockingGet())
                        .filter(state -> state != null && state.getItemId() != null && state.getItemId().equals(mItemId))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(change -> loadMovements(swipeRefreshLayout)));

        mRxDisposer.add("createView_onItemChanged",
                io.reactivex.rxjava3.core.Flowable.merge(
                        mItemChangeNotifier.getAddedItemFlow(),
                        mItemChangeNotifier.getUpdatedItemFlow(),
                        mItemChangeNotifier.getDeletedItemFlow()
                )
                        .filter(itemState -> itemState != null && itemState.getItemId() != null && itemState.getItemId().equals(mItemId))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(change -> loadMovements(swipeRefreshLayout)));

        // Loading state is handled directly in loadMovements method

        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mAdapter != null) {
            mAdapter.dispose(activity);
            mAdapter = null;
        }
        mView = null;
    }

    private View getView() {
        return mView;
    }

    @Override
    public void onRefresh() {
        SwipeRefreshLayout swipeRefreshLayout = getView().findViewById(R.id.container_swipe_refresh_list);
        loadMovements(swipeRefreshLayout);
    }

    private void loadMovements(SwipeRefreshLayout swipeRefreshLayout) {
        Context context = mSvProvider.getContext();
        mRxDisposer.add("loadMovements",
                mQueryItemCmd.findItemStateByItemId(mItemId)
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .flatMap(itemState -> {
                            if (itemState == null) {
                                return io.reactivex.rxjava3.core.Single.just(new Pair<List<StockMovement>, ItemState>(new ArrayList<>(), itemState));
                            }
                            return io.reactivex.rxjava3.core.Single.zip(
                                    mQueryItemUsageCmd.findItemUsageStateByItemId(mItemId)
                                            .map(itemUsageStates -> {
                                                List<StockMovement> movements = new ArrayList<>();
                                                for (ItemUsageState usageState : itemUsageStates) {
                                                    StockMovement movement = new StockMovement();
                                                    movement.type = StockMovement.Type.USAGE;
                                                    movement.date = usageState.getUsageDateTime();
                                                    movement.signedAmount = -usageState.getItemUsageAmount();
                                                    movement.cost = null;
                                                    movement.description = usageState.getItemUsageDescription();
                                                    movement.sourceId = usageState.getItemUsageId();
                                                    movement.sourceState = usageState;
                                                    movements.add(movement);
                                                }
                                                return movements;
                                            }),
                                    mQueryItemPurchaseCmd.findItemPurchaseStateByItemId(mItemId)
                                            .map(itemPurchaseStates -> {
                                                List<StockMovement> movements = new ArrayList<>();
                                                for (ItemPurchaseState purchaseState : itemPurchaseStates) {
                                                    StockMovement movement = new StockMovement();
                                                    movement.type = StockMovement.Type.PURCHASE;
                                                    movement.date = purchaseState.getPurchaseDateTime();
                                                    movement.signedAmount = purchaseState.getItemPurchaseAmount();
                                                    movement.cost = purchaseState.getItemPurchaseCost();
                                                    movement.description = purchaseState.getItemPurchaseDescription();
                                                    movement.sourceId = purchaseState.getItemPurchaseId();
                                                    movement.sourceState = purchaseState;
                                                    movements.add(movement);
                                                }
                                                return movements;
                                            }),
                                    (usageMovements, purchaseMovements) -> {
                                                List<StockMovement> allMovements = new ArrayList<>();
                                                allMovements.addAll(usageMovements);
                                                allMovements.addAll(purchaseMovements);
                                                Collections.sort(allMovements, StockMovement.byDateDesc());
                                                return new Pair<List<StockMovement>, ItemState>(allMovements, itemState);
                                            });
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(pair -> {
                            swipeRefreshLayout.setRefreshing(false);
                            mItemState = pair.second;
                            mStockMovements.clear();
                            List<StockMovement> movements = pair.first;
                            mStockMovements.addAll(movements);
                            // setMovements rebuilds the flat list and calls
                            // notifyDataSetChanged internally.
                            mAdapter.setMovements(mStockMovements);
                            updateSummary();
                        }, throwable -> {
                            swipeRefreshLayout.setRefreshing(false);
                            mLogger.e(TAG, throwable.getMessage(), throwable);
                        }));
    }

    private void updateSummary() {
        if (mItemState == null) return;
        int initial = mItemState.getItemAmount();
        int used = 0;
        int bought = 0;
        for (StockMovement movement : mStockMovements) {
            if (movement.type == StockMovement.Type.USAGE) {
                used += -movement.signedAmount;
            } else {
                bought += movement.signedAmount;
            }
        }
        int remaining = initial - used + bought;
        Context context = mSvProvider.getContext();
        mSummaryTextView.setText(context.getString(R.string.stock_movements_summary, initial, used, bought, remaining));
    }

    @Override
    public void itemUsageItemSV_onItemUsageEditClicked(ItemUsageState itemUsageState) {
        mNavigator.push(Routes.ITEM_USAGE_DETAIL_PAGE,
                ItemUsageDetailPage.Args.with(itemUsageState.clone()));
    }

    @Override
    public void itemUsageItemSV_onItemUsageDeleteClicked(ItemUsageState itemUsageState) {
        String title = mSvProvider.getContext().getString(R.string.title_confirm_delete);
        String message = mSvProvider.getContext().getString(R.string.confirm_delete_, itemUsageState.getItemUsageDescription());
        mNavigator.push(mNavExtDialogConfig.route_confirmDialog(),
                mNavExtDialogConfig.args_confirmDialog(title, message),
                (navigator, navRoute, activity, currentView) -> confirmDeleteUsage(navRoute, itemUsageState));
    }

    @Override
    public void itemPurchaseItemSV_onItemPurchaseEditClicked(ItemPurchaseState itemPurchaseState) {
        mNavigator.push(Routes.ITEM_PURCHASE_DETAIL_PAGE,
                ItemPurchaseDetailPage.Args.with(itemPurchaseState.clone()));
    }

    @Override
    public void itemPurchaseItemSV_onItemPurchaseDeleteClicked(ItemPurchaseState itemPurchaseState) {
        String title = mSvProvider.getContext().getString(R.string.title_confirm_delete);
        String message = mSvProvider.getContext().getString(R.string.confirm_delete_, itemPurchaseState.getItemPurchaseDescription());
        mNavigator.push(mNavExtDialogConfig.route_confirmDialog(),
                mNavExtDialogConfig.args_confirmDialog(title, message),
                (navigator, navRoute, activity, currentView) -> confirmDeletePurchase(navRoute, itemPurchaseState));
    }

    private void confirmDeleteUsage(NavRoute navRoute, ItemUsageState itemUsageState) {
        Boolean isDelete = mNavExtDialogConfig.result_confirmDialog(navRoute);
        if (isDelete != null && isDelete) {
            Context context = mSvProvider.getContext();
            mRxDisposer.add("confirmDeleteUsage",
                    mDeleteItemUsageCmd.execute(itemUsageState)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((deletedItemState, throwable) -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, cause.getMessage(), cause);
                                } else {
                                    mLogger.i(TAG, context.getString(m.co.rh.id.a_personal_stuff.item_usage.R.string.success_delete_item_usage));
                                    SwipeRefreshLayout swipeRefreshLayout = getView().findViewById(R.id.container_swipe_refresh_list);
                                    loadMovements(swipeRefreshLayout);
                                }
                            }));
        }
    }

    private void confirmDeletePurchase(NavRoute navRoute, ItemPurchaseState itemPurchaseState) {
        Boolean isDelete = mNavExtDialogConfig.result_confirmDialog(navRoute);
        if (isDelete != null && isDelete) {
            Context context = mSvProvider.getContext();
            mRxDisposer.add("confirmDeletePurchase",
                    mDeleteItemPurchaseCmd.execute(itemPurchaseState)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((deletedItemState, throwable) -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, cause.getMessage(), cause);
                                } else {
                                    mLogger.i(TAG, context.getString(m.co.rh.id.a_personal_stuff.item_purchase.R.string.success_delete_item_purchase));
                                    SwipeRefreshLayout swipeRefreshLayout = getView().findViewById(R.id.container_swipe_refresh_list);
                                    loadMovements(swipeRefreshLayout);
                                }
                            }));
        }
    }

    private static class Pair<A, B> {
        final A first;
        final B second;
        Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }
}
