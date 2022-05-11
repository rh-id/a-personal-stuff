package m.co.rh.id.a_personal_stuff.item_reminder.ui.component;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.item_reminder.R;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.command.DeleteItemReminderCmd;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.command.PagedItemReminderCmd;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.command.QueryItemReminderCmd;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.notifier.ItemReminderChangeNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class ItemReminderListSV extends StatefulView<Activity> implements RequireComponent<Provider>, SwipeRefreshLayout.OnRefreshListener, ItemReminderItemSV.OnItemReminderDeleteClicked {
    private static final String TAG = ItemReminderListSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient RxDisposer mRxDisposer;
    private transient ItemReminderChangeNotifier mItemReminderChangeNotifier;
    private transient QueryItemReminderCmd mQueryItemReminderCmd;
    private transient DeleteItemReminderCmd mDeleteItemReminderCmd;
    private transient PagedItemReminderCmd mPagedItemReminderCmd;

    private long mItemId;
    private SerialBehaviorSubject<String> mSearchString;
    private transient TextWatcher mSearchTextWatcher;
    private transient ItemReminderRecyclerViewAdapter mItemReminderRecyclerViewAdapter;
    private transient RecyclerView.OnScrollListener mItemsOnScrollListener;

    public ItemReminderListSV(long itemId) {
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
        mItemReminderChangeNotifier = mSvProvider.get(ItemReminderChangeNotifier.class);
        mQueryItemReminderCmd = mSvProvider.get(QueryItemReminderCmd.class);
        mDeleteItemReminderCmd = mSvProvider.get(DeleteItemReminderCmd.class);
        mPagedItemReminderCmd = mSvProvider.get(PagedItemReminderCmd.class);
        mPagedItemReminderCmd.setItemId(mItemId);
        mPagedItemReminderCmd.refresh();
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
        mItemReminderRecyclerViewAdapter = new ItemReminderRecyclerViewAdapter(mPagedItemReminderCmd, this, mNavigator, this);
        mItemsOnScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    mPagedItemReminderCmd.loadNextPage();
                }
            }
        };
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.list_item_reminder, container, false);
        SwipeRefreshLayout swipeRefreshLayout = rootLayout.findViewById(R.id.container_swipe_refresh_list);
        swipeRefreshLayout.setOnRefreshListener(this);
        RecyclerView recyclerView = rootLayout.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mItemReminderRecyclerViewAdapter);
        recyclerView.addOnScrollListener(mItemsOnScrollListener);
        mRxDisposer.add("createView_onItemOnLoading",
                mPagedItemReminderCmd.getLoadingFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(swipeRefreshLayout::setRefreshing));
        mRxDisposer.add("createView_onItemRemindersChanged",
                mPagedItemReminderCmd.getItemRemindersFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemUsageStates -> mItemReminderRecyclerViewAdapter.notifyItemRefreshed()));
        mRxDisposer.add("createView_onItemReminderAdded",
                mItemReminderChangeNotifier.getAddedFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemReminderRecyclerViewAdapter::notifyItemAdded));
        mRxDisposer.add("createView_onItemReminderDeleted",
                mItemReminderChangeNotifier.getDeletedFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mItemReminderRecyclerViewAdapter::notifyItemDeleted));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mItemReminderRecyclerViewAdapter != null) {
            mItemReminderRecyclerViewAdapter.dispose(activity);
            mItemReminderRecyclerViewAdapter = null;
        }
    }

    @Override
    public void onRefresh() {
        mPagedItemReminderCmd.refresh();
    }

    @Override
    public void itemReminderItemSV_onItemReminderDeleteClicked(ItemReminder itemReminder) {
        Context context = mSvProvider.getContext();
        String title = context.getString(R.string.title_confirm_delete);
        String message = context.getString(R.string.confirm_delete_, itemReminder.message);
        mNavigator.push(mNavExtDialogConfig.route_confirmDialog(),
                mNavExtDialogConfig.args_confirmDialog(title, message),
                (navigator, navRoute, activity, currentView) -> confirmDeleteItem(navRoute, itemReminder));
    }

    private void confirmDeleteItem(NavRoute navRoute, ItemReminder itemReminder) {
        Boolean isDelete = mNavExtDialogConfig.result_confirmDialog(navRoute);
        if (isDelete != null && isDelete) {
            Context context = mSvProvider.getContext();
            mRxDisposer.add("confirmDeleteItem",
                    mDeleteItemReminderCmd.execute(itemReminder)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((deletedItemMaintenanceState, throwable) -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, cause.getMessage(), cause);
                                } else {
                                    mLogger.i(TAG, context.getString(R.string.success_delete_item_reminder));
                                    mItemReminderRecyclerViewAdapter.notifyItemDeleted(deletedItemMaintenanceState);
                                }
                            }));
        }
    }
}
