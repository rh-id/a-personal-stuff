package m.co.rh.id.a_personal_stuff.settings.provider.component;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.aprovider.Provider;

public class SettingsSharedPreferences {
    private static final String SHARED_PREFERENCES_NAME = "SettingsSharedPreferences";

    /** Detailed (full) item card layout — the historical default. */
    public static final int ITEM_VIEW_MODE_DETAILED = 0;
    /** Compact item card: name, amount, and thumbnail only. */
    public static final int ITEM_VIEW_MODE_COMPACT = 1;
    /** Default: expiry alerts are enabled. */
    public static final boolean DEFAULT_EXPIRY_ALERT_ENABLED = true;
    /** Default number of days an item's expiry date is alerted in advance. */
    public static final int DEFAULT_EXPIRY_ALERT_LEAD_DAYS = 7;
    /** Default: low stock alerts are enabled. */
    public static final boolean DEFAULT_LOW_STOCK_ALERT_ENABLED = true;

    private ExecutorService mExecutorService;
    private SharedPreferences mSharedPreferences;

    private BehaviorSubject<Integer> mSelectedTheme;
    private String mSelectedThemeKey;
    private BehaviorSubject<Integer> mItemViewMode;
    private String mItemViewModeKey;
    private BehaviorSubject<Boolean> mExpiryAlertEnabled;
    private String mExpiryAlertEnabledKey;
    private BehaviorSubject<Integer> mExpiryAlertLeadDays;
    private String mExpiryAlertLeadDaysKey;
    private BehaviorSubject<Boolean> mLowStockAlertEnabled;
    private String mLowStockAlertEnabledKey;

    public SettingsSharedPreferences(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mSharedPreferences = provider.getContext().getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        mSelectedTheme = BehaviorSubject.createDefault(-1);
        // Detailed view is the historical default; keeps existing users' UI unchanged.
        mItemViewMode = BehaviorSubject.createDefault(ITEM_VIEW_MODE_DETAILED);
        mExpiryAlertEnabled = BehaviorSubject.createDefault(DEFAULT_EXPIRY_ALERT_ENABLED);
        mExpiryAlertLeadDays = BehaviorSubject.createDefault(DEFAULT_EXPIRY_ALERT_LEAD_DAYS);
        mLowStockAlertEnabled = BehaviorSubject.createDefault(DEFAULT_LOW_STOCK_ALERT_ENABLED);
        initValue();
    }

    private void initValue() {
        mSelectedThemeKey = SHARED_PREFERENCES_NAME
                + ".selectedTheme";

        int selectedTheme = mSharedPreferences.getInt(
                mSelectedThemeKey,
                mSelectedTheme.getValue());
        setSelectedTheme(selectedTheme);

        mItemViewModeKey = SHARED_PREFERENCES_NAME
                + ".itemViewMode";
        int itemViewMode = mSharedPreferences.getInt(
                mItemViewModeKey,
                mItemViewMode.getValue());
        setItemViewMode(itemViewMode);

        mExpiryAlertEnabledKey = SHARED_PREFERENCES_NAME
                + ".expiryAlertEnabled";
        boolean expiryAlertEnabled = mSharedPreferences.getBoolean(
                mExpiryAlertEnabledKey,
                mExpiryAlertEnabled.getValue());
        setExpiryAlertEnabled(expiryAlertEnabled);

        mExpiryAlertLeadDaysKey = SHARED_PREFERENCES_NAME
                + ".expiryAlertLeadDays";
        int expiryAlertLeadDays = mSharedPreferences.getInt(
                mExpiryAlertLeadDaysKey,
                mExpiryAlertLeadDays.getValue());
        setExpiryAlertLeadDays(expiryAlertLeadDays);

        mLowStockAlertEnabledKey = SHARED_PREFERENCES_NAME
                + ".lowStockAlertEnabled";
        boolean lowStockAlertEnabled = mSharedPreferences.getBoolean(
                mLowStockAlertEnabledKey,
                mLowStockAlertEnabled.getValue());
        setLowStockAlertEnabled(lowStockAlertEnabled);
    }

    private void selectedTheme(int setting) {
        mSelectedTheme.onNext(setting);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mSelectedThemeKey, setting)
                        .commit());
    }

    public void setSelectedTheme(int setting) {
        selectedTheme(setting);
    }

    public Flowable<Integer> getSelectedThemeFlow() {
        return Flowable.fromObservable(mSelectedTheme, BackpressureStrategy.BUFFER);
    }

    private void itemViewMode(int mode) {
        mItemViewMode.onNext(mode);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mItemViewModeKey, mode)
                        .commit());
    }

    public void setItemViewMode(int mode) {
        itemViewMode(mode);
    }

    public int getItemViewMode() {
        Integer current = mItemViewMode.getValue();
        return current != null ? current : ITEM_VIEW_MODE_DETAILED;
    }

    public Flowable<Integer> getItemViewModeFlow() {
        return Flowable.fromObservable(mItemViewMode, BackpressureStrategy.BUFFER);
    }

    private void expiryAlertEnabled(boolean enabled) {
        mExpiryAlertEnabled.onNext(enabled);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mExpiryAlertEnabledKey, enabled)
                        .commit());
    }

    public void setExpiryAlertEnabled(boolean enabled) {
        expiryAlertEnabled(enabled);
    }

    public boolean isExpiryAlertEnabled() {
        Boolean current = mExpiryAlertEnabled.getValue();
        return current != null ? current : DEFAULT_EXPIRY_ALERT_ENABLED;
    }

    public Flowable<Boolean> getExpiryAlertEnabledFlow() {
        return Flowable.fromObservable(mExpiryAlertEnabled, BackpressureStrategy.BUFFER);
    }

    private void expiryAlertLeadDays(int days) {
        mExpiryAlertLeadDays.onNext(days);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mExpiryAlertLeadDaysKey, days)
                        .commit());
    }

    public void setExpiryAlertLeadDays(int days) {
        expiryAlertLeadDays(days);
    }

    public int getExpiryAlertLeadDays() {
        Integer current = mExpiryAlertLeadDays.getValue();
        return current != null ? current : DEFAULT_EXPIRY_ALERT_LEAD_DAYS;
    }

    public Flowable<Integer> getExpiryAlertLeadDaysFlow() {
        return Flowable.fromObservable(mExpiryAlertLeadDays, BackpressureStrategy.BUFFER);
    }

    private void lowStockAlertEnabled(boolean enabled) {
        mLowStockAlertEnabled.onNext(enabled);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mLowStockAlertEnabledKey, enabled)
                        .commit());
    }

    public void setLowStockAlertEnabled(boolean enabled) {
        lowStockAlertEnabled(enabled);
    }

    public boolean isLowStockAlertEnabled() {
        Boolean current = mLowStockAlertEnabled.getValue();
        return current != null ? current : DEFAULT_LOW_STOCK_ALERT_ENABLED;
    }

    public Flowable<Boolean> getLowStockAlertEnabledFlow() {
        return Flowable.fromObservable(mLowStockAlertEnabled, BackpressureStrategy.BUFFER);
    }
}
