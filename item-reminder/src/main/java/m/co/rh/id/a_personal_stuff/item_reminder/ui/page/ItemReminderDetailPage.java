package m.co.rh.id.a_personal_stuff.item_reminder.ui.page;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.function.Function;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.base.ui.component.adapter.SuggestionAdapter;
import m.co.rh.id.a_personal_stuff.item_reminder.R;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.command.NewItemReminderCmd;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.command.QueryItemReminderCmd;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class ItemReminderDetailPage extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener, Toolbar.OnMenuItemClickListener {

    private static final String TAG = ItemReminderDetailPage.class.getName();
    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;
    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient RxDisposer mRxDisposer;
    private transient NewItemReminderCmd mNewItemReminderCmd;
    private transient QueryItemReminderCmd mQueryItemReminderCmd;

    @NavInject
    private AppBarSV mAppBarSV;
    private SerialBehaviorSubject<ItemReminder> mItemReminder;
    private DateFormat mDateFormat;

    private transient TextWatcher mReminderDateTimeTextWatcher;
    private transient TextWatcher mMessageTextWatcher;
    private transient Function<String, Collection<String>> mSuggestionMessageQuery;

    public ItemReminderDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_item_reminder_detail);
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mNavExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mNewItemReminderCmd = mSvProvider.get(NewItemReminderCmd.class);
        mQueryItemReminderCmd = mSvProvider.get(QueryItemReminderCmd.class);
        mReminderDateTimeTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                mNewItemReminderCmd.valid(mItemReminder.getValue());
            }
        };
        mMessageTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                ItemReminder itemReminder = mItemReminder.getValue();
                itemReminder.message = editable.toString();
                mNewItemReminderCmd.valid(itemReminder);
            }
        };
        if (mItemReminder == null) {
            ItemReminder itemReminder = new ItemReminder();
            itemReminder.itemId = getItemId();
            mItemReminder = new SerialBehaviorSubject<>(itemReminder);
        }
        mSuggestionMessageQuery = (s) -> mQueryItemReminderCmd.searchItemMaintenanceDescription(s)
                .blockingGet();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_reminder_detail, container, false);
        EditText inputReminderDateTime = rootLayout.findViewById(R.id.input_text_reminder_date_time);
        inputReminderDateTime.setOnClickListener(this);
        inputReminderDateTime.addTextChangedListener(mReminderDateTimeTextWatcher);
        AutoCompleteTextView inputMessage = rootLayout.findViewById(R.id.input_text_message);
        inputMessage.setThreshold(1);
        inputMessage.setAdapter(new SuggestionAdapter
                (activity, mSuggestionMessageQuery));
        inputMessage.addTextChangedListener(mMessageTextWatcher);
        mAppBarSV.setTitle(activity.getString(R.string.title_add_item_reminder));
        mAppBarSV.setMenuItemClick(this);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, containerAppBar));
        mRxDisposer.add("createView_onItemReminderChanged",
                mItemReminder.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemReminder -> {
                            if (itemReminder.reminderDateTime != null) {
                                inputReminderDateTime.setText(mDateFormat.format(itemReminder.reminderDateTime));
                            } else {
                                inputReminderDateTime.setText(null);
                            }
                            inputMessage.setText(itemReminder.message);
                        }));
        mRxDisposer.add("createView_onReminderDateTimeValid",
                mNewItemReminderCmd.getRemiderDateTimeValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputReminderDateTime.setError(s);
                            } else {
                                inputReminderDateTime.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onMessageValid",
                mNewItemReminderCmd.getMessageValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputMessage.setError(s);
                            } else {
                                inputMessage.setError(null);
                            }
                        }));
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
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.input_text_reminder_date_time) {
            mNavigator.push(mNavExtDialogConfig.route_dateTimePickerDialog(),
                    mNavExtDialogConfig.args_dateTimePickerDialog(true, mItemReminder.getValue().reminderDateTime),
                    (navigator, navRoute, activity, currentView) -> reminderDateTimeSelected(navRoute));
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            ItemReminder itemReminder = mItemReminder.getValue();
            if (mNewItemReminderCmd.valid(itemReminder)) {
                mRxDisposer.add("onMenuItemClick_save",
                        mNewItemReminderCmd.execute(itemReminder)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((itemUsageState, throwable) -> {
                                    if (throwable != null) {
                                        Throwable cause = throwable.getCause();
                                        if (cause == null) cause = throwable;
                                        mLogger.e(TAG, cause.getMessage(), cause);
                                    } else {
                                        mLogger.i(TAG, mSvProvider.getContext()
                                                .getString(R.string.success_add_item_reminder));
                                        mNavigator.pop();
                                    }
                                }));
            } else {
                mLogger.i(TAG, mNewItemReminderCmd.getValidationError());
            }
        }
        return false;
    }

    private void reminderDateTimeSelected(NavRoute navRoute) {
        Date result = mNavExtDialogConfig.result_dateTimePickerDialog(navRoute);
        if (result != null) {
            ItemReminder itemReminder = mItemReminder.getValue();
            itemReminder.reminderDateTime = result;
            mItemReminder.onNext(itemReminder);
        }
    }

    private Long getItemId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemId;
        }
        return null;
    }

    public static class Args implements Serializable {
        public static Args with(long itemId) {
            Args args = new Args();
            args.itemId = itemId;
            return args;
        }

        static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable args = navRoute.getRouteArgs();
                if (args instanceof Args) {
                    return (Args) args;
                }
            }
            return null;
        }

        private Long itemId;
    }
}
