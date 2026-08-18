package m.co.rh.id.a_personal_stuff.item_checklist.ui.page;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.recyclerview.widget.RecyclerView;

import java.io.Serializable;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.item_checklist.R;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistProgress;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.AddItemChecklistItemCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.provider.command.QueryItemChecklistCmd;
import m.co.rh.id.a_personal_stuff.item_checklist.ui.component.ItemChecklistRecyclerViewAdapter;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemChecklistSelectPage extends StatefulView<Activity> implements RequireComponent<Provider> {

    private static final String TAG = ItemChecklistSelectPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    @NavInject
    private AppBarSV mAppBarSV;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    private transient QueryItemChecklistCmd mQueryItemChecklistCmd;
    private transient AddItemChecklistItemCmd mAddItemChecklistItemCmd;

    private Args mArgs;
    private transient ItemChecklistRecyclerViewAdapter mAdapter;
    private boolean mAddingItem;
    private SerialBehaviorSubject<String> mSearchString;
    private transient String mSearch;
    private transient TextWatcher mSearchTextWatcher;

    public ItemChecklistSelectPage() {
        this(null);
    }

    public ItemChecklistSelectPage(Args args) {
        mArgs = args;
        mAppBarSV = new AppBarSV();
        mSearchString = new SerialBehaviorSubject<>();
        mSearch = "";
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mQueryItemChecklistCmd = mSvProvider.get(QueryItemChecklistCmd.class);
        mAddItemChecklistItemCmd = mSvProvider.get(AddItemChecklistItemCmd.class);

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

        mAdapter = new ItemChecklistRecyclerViewAdapter(
                itemChecklistState -> addItemToChecklist(itemChecklistState),
                mNavigator, this);
        SettingsSharedPreferences settings = mSvProvider.get(SettingsSharedPreferences.class);
        mRxDisposer.add("provideComponent_itemViewModeChanged",
                settings.getItemViewModeFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mode ->
                                mAdapter.setCompact(
                                        mode == SettingsSharedPreferences.ITEM_VIEW_MODE_COMPACT)));
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_checklist_select, container, false);

        mAppBarSV.setTitle(activity.getString(R.string.title_add_to_checklist));
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSV.buildView(activity, appBarContainer));

        EditText searchEditText = rootLayout.findViewById(R.id.edit_text_search);
        searchEditText.addTextChangedListener(mSearchTextWatcher);

        RecyclerView recyclerView = rootLayout.findViewById(R.id.recycler_view);
        recyclerView.setAdapter(mAdapter);

        mRxDisposer.add("createView_onSearch",
                mSearchString.getSubject().debounce(700, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(search -> {
                            mSearch = search;
                            reloadList();
                        }));

        reloadList();

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

    private void reloadList() {
        mRxDisposer.add("reloadList",
                mQueryItemChecklistCmd.findChecklistStatesNotContainingItem(mArgs.itemId, mSearch)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((itemChecklistStates, throwable) -> {
                            if (throwable != null) {
                                mLogger.e(TAG, throwable.getMessage(), throwable);
                            } else {
                                mAdapter.setItems(itemChecklistStates);
                                loadProgress();
                            }
                        }));
    }

    private void loadProgress() {
        mRxDisposer.add("loadProgress",
                mQueryItemChecklistCmd.findAllProgress()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(progressList -> {
                            mAdapter.setProgressMap(progressList);
                            mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount());
                        }));
    }

    private void addItemToChecklist(ItemChecklistState itemChecklistState) {
        if (mAddingItem) {
            return;
        }
        mAddingItem = true;
        mRxDisposer.add("addItemToChecklist",
                mAddItemChecklistItemCmd.execute(itemChecklistState.getChecklistId(), Collections.singletonList(mArgs.itemId))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((count, throwable) -> {
                            if (throwable != null) {
                                mAddingItem = false;
                                mLogger.e(TAG, throwable.getMessage(), throwable);
                            } else {
                                Activity activity = mNavigator.getActivity();
                                if (activity != null) {
                                    mLogger.i(TAG, activity.getString(R.string.success_add_item_to_checklist_,
                                            itemChecklistState.getTitle()));
                                }
                                mNavigator.pop();
                            }
                        }));
    }

    public static class Args implements Serializable {
        public static Args with(Long itemId) {
            Args args = new Args();
            args.itemId = itemId;
            return args;
        }

        public static Args of(Serializable args) {
            if (args instanceof Args) {
                return (Args) args;
            }
            return null;
        }

        private Long itemId;
    }
}
