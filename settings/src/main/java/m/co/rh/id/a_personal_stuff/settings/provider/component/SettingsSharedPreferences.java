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

    private ExecutorService mExecutorService;
    private SharedPreferences mSharedPreferences;

    private BehaviorSubject<Integer> mSelectedTheme;
    private String mSelectedThemeKey;
    private BehaviorSubject<Integer> mItemViewMode;
    private String mItemViewModeKey;

    public SettingsSharedPreferences(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mSharedPreferences = provider.getContext().getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        mSelectedTheme = BehaviorSubject.createDefault(-1);
        // Detailed view is the historical default; keeps existing users' UI unchanged.
        mItemViewMode = BehaviorSubject.createDefault(ITEM_VIEW_MODE_DETAILED);
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
}
