package m.co.rh.id.a_personal_stuff.settings.ui.component;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.settings.R;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class AlertSettingsMenuSV extends StatefulView<Activity> implements RequireComponent<Provider>,
        CompoundButton.OnCheckedChangeListener, RadioGroup.OnCheckedChangeListener {

    // fire-and-forget: no result handling needed for v1, the digest worker
    // already skips posting when the permission is denied
    private static final int REQUEST_CODE_PERMISSION_POST_NOTIFICATIONS = 2;

    private transient Provider mSvProvider;
    private transient SettingsSharedPreferences mSettingsSharedPreferences;
    private transient Activity mActivity;
    private transient RadioGroup mRadioGroupLeadDays;
    private transient TextView mTextNotifyWithin;

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mSettingsSharedPreferences = mSvProvider.get(SettingsSharedPreferences.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        mActivity = activity;
        View view = activity.getLayoutInflater().inflate(R.layout.menu_alert_settings, container, false);
        CompoundButton switchExpiryAlert = view.findViewById(R.id.switch_expiry_alert);
        RadioGroup radioGroupLeadDays = view.findViewById(R.id.radioGroup_lead_days);
        TextView textNotifyWithin = view.findViewById(R.id.text_notify_within);
        CompoundButton switchLowStockAlert = view.findViewById(R.id.switch_low_stock_alert);
        // assign before use so setLeadDaysEnabled can reach the group
        mRadioGroupLeadDays = radioGroupLeadDays;
        mTextNotifyWithin = textNotifyWithin;
        // apply current values before attaching listeners so initialization
        // does not write back to the shared preferences
        switchExpiryAlert.setChecked(mSettingsSharedPreferences.isExpiryAlertEnabled());
        radioGroupLeadDays.check(toLeadDaysRadioId(mSettingsSharedPreferences.getExpiryAlertLeadDays()));
        setLeadDaysEnabled(mSettingsSharedPreferences.isExpiryAlertEnabled());
        switchLowStockAlert.setChecked(mSettingsSharedPreferences.isLowStockAlertEnabled());
        switchExpiryAlert.setOnCheckedChangeListener(this);
        radioGroupLeadDays.setOnCheckedChangeListener(this);
        switchLowStockAlert.setOnCheckedChangeListener(this);
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mActivity = null;
        mRadioGroupLeadDays = null;
        mTextNotifyWithin = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        int id = compoundButton.getId();
        if (id == R.id.switch_expiry_alert) {
            mSettingsSharedPreferences.setExpiryAlertEnabled(isChecked);
            setLeadDaysEnabled(isChecked);
        } else if (id == R.id.switch_low_stock_alert) {
            mSettingsSharedPreferences.setLowStockAlertEnabled(isChecked);
        }
        if (isChecked) {
            requestNotificationPermissionIfNeeded();
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
        int leadDays = SettingsSharedPreferences.DEFAULT_EXPIRY_ALERT_LEAD_DAYS;
        if (checkedId == R.id.radio_3_days) {
            leadDays = 3;
        } else if (checkedId == R.id.radio_14_days) {
            leadDays = 14;
        }
        mSettingsSharedPreferences.setExpiryAlertLeadDays(leadDays);
    }

    // ViewGroup.setEnabled does not propagate to children, so each radio
    // button must be disabled individually for the state to be visible
    private void setLeadDaysEnabled(boolean enabled) {
        mRadioGroupLeadDays.setEnabled(enabled);
        for (int i = 0; i < mRadioGroupLeadDays.getChildCount(); i++) {
            mRadioGroupLeadDays.getChildAt(i).setEnabled(enabled);
        }
        mTextNotifyWithin.setEnabled(enabled);
    }

    private void requestNotificationPermissionIfNeeded() {
        Activity activity = mActivity;
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_CODE_PERMISSION_POST_NOTIFICATIONS);
        }
    }

    private static int toLeadDaysRadioId(int leadDays) {
        if (leadDays <= 3) {
            return R.id.radio_3_days;
        }
        if (leadDays <= 7) {
            return R.id.radio_7_days;
        }
        return R.id.radio_14_days;
    }
}
