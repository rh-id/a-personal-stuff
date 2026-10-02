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
    /** Default: Material You dynamic colors are enabled (when supported by the device). */
    public static final boolean DEFAULT_DYNAMIC_COLORS_ENABLED = true;
    /** Default: automatic scheduled backup is disabled. */
    public static final boolean DEFAULT_AUTO_BACKUP_ENABLED = false;
    /** Default backup interval in days: 30. */
    public static final int DEFAULT_AUTO_BACKUP_INTERVAL_DAYS = 30;
    /**
     * Sentinel for "no SAF folder selected". Strings must never be null here:
     * {@code BehaviorSubject.createDefault(null)}/{@code onNext(null)} throw
     * NPE in RxJava3, so an empty string means none.
     */
    public static final String DEFAULT_AUTO_BACKUP_TREE_URI = "";
    /** Sentinel for "no recorded backup failure". */
    public static final String DEFAULT_AUTO_BACKUP_LAST_FAILURE = "";
    /** Default last successful backup timestamp (never backed up). */
    public static final long DEFAULT_AUTO_BACKUP_LAST_SUCCESS_DATE_TIME = 0L;

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
    private BehaviorSubject<Boolean> mDynamicColorsEnabled;
    private String mDynamicColorsEnabledKey;
    private BehaviorSubject<Boolean> mAutoBackupEnabled;
    private String mAutoBackupEnabledKey;
    private BehaviorSubject<Integer> mAutoBackupIntervalDays;
    private String mAutoBackupIntervalDaysKey;
    private BehaviorSubject<String> mAutoBackupTreeUri;
    private String mAutoBackupTreeUriKey;
    private BehaviorSubject<Long> mAutoBackupLastSuccessDateTime;
    private String mAutoBackupLastSuccessDateTimeKey;
    private BehaviorSubject<String> mAutoBackupLastFailure;
    private String mAutoBackupLastFailureKey;

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
        mDynamicColorsEnabled = BehaviorSubject.createDefault(DEFAULT_DYNAMIC_COLORS_ENABLED);
        mAutoBackupEnabled = BehaviorSubject.createDefault(DEFAULT_AUTO_BACKUP_ENABLED);
        mAutoBackupIntervalDays = BehaviorSubject.createDefault(DEFAULT_AUTO_BACKUP_INTERVAL_DAYS);
        mAutoBackupTreeUri = BehaviorSubject.createDefault(DEFAULT_AUTO_BACKUP_TREE_URI);
        mAutoBackupLastSuccessDateTime = BehaviorSubject.createDefault(DEFAULT_AUTO_BACKUP_LAST_SUCCESS_DATE_TIME);
        mAutoBackupLastFailure = BehaviorSubject.createDefault(DEFAULT_AUTO_BACKUP_LAST_FAILURE);
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

        mDynamicColorsEnabledKey = SHARED_PREFERENCES_NAME
                + ".dynamicColorsEnabled";
        boolean dynamicColorsEnabled = mSharedPreferences.getBoolean(
                mDynamicColorsEnabledKey,
                mDynamicColorsEnabled.getValue());
        setDynamicColorsEnabled(dynamicColorsEnabled);

        mAutoBackupEnabledKey = SHARED_PREFERENCES_NAME
                + ".autoBackupEnabled";
        boolean autoBackupEnabled = mSharedPreferences.getBoolean(
                mAutoBackupEnabledKey,
                mAutoBackupEnabled.getValue());
        setAutoBackupEnabled(autoBackupEnabled);

        mAutoBackupIntervalDaysKey = SHARED_PREFERENCES_NAME
                + ".autoBackupIntervalDays";
        int autoBackupIntervalDays = mSharedPreferences.getInt(
                mAutoBackupIntervalDaysKey,
                mAutoBackupIntervalDays.getValue());
        setAutoBackupIntervalDays(autoBackupIntervalDays);

        mAutoBackupTreeUriKey = SHARED_PREFERENCES_NAME
                + ".autoBackupTreeUri";
        String autoBackupTreeUri = mSharedPreferences.getString(
                mAutoBackupTreeUriKey,
                mAutoBackupTreeUri.getValue());
        setAutoBackupTreeUri(autoBackupTreeUri);

        mAutoBackupLastSuccessDateTimeKey = SHARED_PREFERENCES_NAME
                + ".autoBackupLastSuccessDateTime";
        long autoBackupLastSuccessDateTime = mSharedPreferences.getLong(
                mAutoBackupLastSuccessDateTimeKey,
                mAutoBackupLastSuccessDateTime.getValue());
        setAutoBackupLastSuccessDateTime(autoBackupLastSuccessDateTime);

        mAutoBackupLastFailureKey = SHARED_PREFERENCES_NAME
                + ".autoBackupLastFailure";
        String autoBackupLastFailure = mSharedPreferences.getString(
                mAutoBackupLastFailureKey,
                mAutoBackupLastFailure.getValue());
        setAutoBackupLastFailure(autoBackupLastFailure);
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

    private void dynamicColorsEnabled(boolean enabled) {
        mDynamicColorsEnabled.onNext(enabled);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mDynamicColorsEnabledKey, enabled)
                        .commit());
    }

    public void setDynamicColorsEnabled(boolean enabled) {
        dynamicColorsEnabled(enabled);
    }

    public boolean isDynamicColorsEnabled() {
        Boolean current = mDynamicColorsEnabled.getValue();
        return current != null ? current : DEFAULT_DYNAMIC_COLORS_ENABLED;
    }

    public Flowable<Boolean> getDynamicColorsEnabledFlow() {
        return Flowable.fromObservable(mDynamicColorsEnabled, BackpressureStrategy.BUFFER);
    }

    private void autoBackupEnabled(boolean enabled) {
        mAutoBackupEnabled.onNext(enabled);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mAutoBackupEnabledKey, enabled)
                        .commit());
    }

    public void setAutoBackupEnabled(boolean enabled) {
        autoBackupEnabled(enabled);
    }

    public boolean isAutoBackupEnabled() {
        Boolean current = mAutoBackupEnabled.getValue();
        return current != null ? current : DEFAULT_AUTO_BACKUP_ENABLED;
    }

    public Flowable<Boolean> getAutoBackupEnabledFlow() {
        return Flowable.fromObservable(mAutoBackupEnabled, BackpressureStrategy.BUFFER);
    }

    private void autoBackupIntervalDays(int days) {
        mAutoBackupIntervalDays.onNext(days);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mAutoBackupIntervalDaysKey, days)
                        .commit());
    }

    public void setAutoBackupIntervalDays(int days) {
        autoBackupIntervalDays(days);
    }

    public int getAutoBackupIntervalDays() {
        Integer current = mAutoBackupIntervalDays.getValue();
        return current != null ? current : DEFAULT_AUTO_BACKUP_INTERVAL_DAYS;
    }

    public Flowable<Integer> getAutoBackupIntervalDaysFlow() {
        return Flowable.fromObservable(mAutoBackupIntervalDays, BackpressureStrategy.BUFFER);
    }

    private void autoBackupTreeUri(String treeUri) {
        // the subject must never carry null: RxJava3 BehaviorSubject throws
        // NPE on createDefault(null)/onNext(null), so "" means "no folder"
        String value = treeUri != null ? treeUri : DEFAULT_AUTO_BACKUP_TREE_URI;
        mAutoBackupTreeUri.onNext(value);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putString(mAutoBackupTreeUriKey, value)
                        .commit());
    }

    public void setAutoBackupTreeUri(String treeUri) {
        autoBackupTreeUri(treeUri);
    }

    public String getAutoBackupTreeUri() {
        String current = mAutoBackupTreeUri.getValue();
        return current != null ? current : DEFAULT_AUTO_BACKUP_TREE_URI;
    }

    public Flowable<String> getAutoBackupTreeUriFlow() {
        return Flowable.fromObservable(mAutoBackupTreeUri, BackpressureStrategy.BUFFER);
    }

    private void autoBackupLastSuccessDateTime(long dateTime) {
        mAutoBackupLastSuccessDateTime.onNext(dateTime);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putLong(mAutoBackupLastSuccessDateTimeKey, dateTime)
                        .commit());
    }

    public void setAutoBackupLastSuccessDateTime(long dateTime) {
        autoBackupLastSuccessDateTime(dateTime);
    }

    public long getAutoBackupLastSuccessDateTime() {
        Long current = mAutoBackupLastSuccessDateTime.getValue();
        return current != null ? current : DEFAULT_AUTO_BACKUP_LAST_SUCCESS_DATE_TIME;
    }

    public Flowable<Long> getAutoBackupLastSuccessDateTimeFlow() {
        return Flowable.fromObservable(mAutoBackupLastSuccessDateTime, BackpressureStrategy.BUFFER);
    }

    private void autoBackupLastFailure(String failure) {
        // same null-safety rule as the tree uri: "" means "no failure"
        String value = failure != null ? failure : DEFAULT_AUTO_BACKUP_LAST_FAILURE;
        mAutoBackupLastFailure.onNext(value);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putString(mAutoBackupLastFailureKey, value)
                        .commit());
    }

    public void setAutoBackupLastFailure(String failure) {
        autoBackupLastFailure(failure);
    }

    public String getAutoBackupLastFailure() {
        String current = mAutoBackupLastFailure.getValue();
        return current != null ? current : DEFAULT_AUTO_BACKUP_LAST_FAILURE;
    }

    public Flowable<String> getAutoBackupLastFailureFlow() {
        return Flowable.fromObservable(mAutoBackupLastFailure, BackpressureStrategy.BUFFER);
    }
}
