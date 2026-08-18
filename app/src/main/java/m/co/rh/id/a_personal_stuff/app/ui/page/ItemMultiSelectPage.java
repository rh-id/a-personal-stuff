package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.command.QueryItemCmd;
import m.co.rh.id.a_personal_stuff.app.ui.component.item.ItemListSV;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemMultiSelectPage extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private static final String TAG = ItemMultiSelectPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient ILogger mLogger;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient QueryItemCmd mQueryItemCmd;

    @NavInject
    private ItemListSV mItemListSV;

    public ItemMultiSelectPage() {
        mItemListSV = new ItemListSV(false, true);
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mQueryItemCmd = mSvProvider.get(QueryItemCmd.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_multi_select, container, false);
        ViewGroup content = rootLayout.findViewById(R.id.container_content);
        content.addView(mItemListSV.buildView(activity, content));
        Button cancelButton = rootLayout.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);
        Button okButton = rootLayout.findViewById(R.id.button_ok);
        okButton.setOnClickListener(this);
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mItemListSV.dispose(activity);
        mItemListSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_cancel) {
            mNavigator.pop();
        } else if (id == R.id.button_ok) {
            List<Long> selectedIds = new ArrayList<>(mItemListSV.getSelectedIds());
            if (selectedIds.isEmpty()) {
                mLogger.i(TAG, view.getContext().getString(R.string.error_please_select_item));
            } else {
                mRxDisposer.add("onClick_ok",
                        mQueryItemCmd.findItemStateByItemIds(selectedIds)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((itemStates, throwable) -> {
                                    if (throwable != null) {
                                        mLogger.e(TAG, throwable.getMessage(), throwable);
                                    } else if (!itemStates.isEmpty()) {
                                        mNavigator.pop(Result.with(itemStates));
                                    }
                                }));
            }
        }
    }

    public static class Result implements Serializable {
        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable result = navRoute.getRouteResult();
                if (result instanceof Result) {
                    return (Result) result;
                }
            }
            return null;
        }

        static Result with(List<ItemState> itemStates) {
            Result result = new Result();
            result.itemStates = new ArrayList<>(itemStates);
            return result;
        }

        private ArrayList<ItemState> itemStates;

        public ArrayList<ItemState> getItemStates() {
            return itemStates;
        }
    }
}
