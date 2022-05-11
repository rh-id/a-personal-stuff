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
    private ExecutorService mExecutorService;
    private SharedPreferences mSharedPreferences;

    private BehaviorSubject<Integer> mSelectedTheme;
    private String mSelectedThemeKey;

    public SettingsSharedPreferences(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mSharedPreferences = provider.getContext().getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        mSelectedTheme = BehaviorSubject.createDefault(-1);
        initValue();
    }

    private void initValue() {
        mSelectedThemeKey = SHARED_PREFERENCES_NAME
                + ".selectedTheme";

        int selectedTheme = mSharedPreferences.getInt(
                mSelectedThemeKey,
                mSelectedTheme.getValue());
        setSelectedTheme(selectedTheme);
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
}
