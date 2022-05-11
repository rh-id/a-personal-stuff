package m.co.rh.id.a_personal_stuff.settings.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatDelegate;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.settings.R;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ThemeMenuSV extends StatefulView<Activity> implements RequireComponent<Provider>, RadioGroup.OnCheckedChangeListener {

    private transient Provider mSvProvider;
    private transient SettingsSharedPreferences mSettingsSharedPreferences;
    private transient RxDisposer mRxDisposer;

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mSettingsSharedPreferences = mSvProvider.get(SettingsSharedPreferences.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_theme, container, false);
        RadioGroup radioGroup = view.findViewById(R.id.radioGroup);
        radioGroup.setOnCheckedChangeListener(this);
        mRxDisposer.add("createView_onSelectedThemeChanged",
                mSettingsSharedPreferences.getSelectedThemeFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(theme -> {
                            int result = R.id.radio_system;
                            if (theme == AppCompatDelegate.MODE_NIGHT_NO) {
                                result = R.id.radio_light;
                            } else if (theme == AppCompatDelegate.MODE_NIGHT_YES) {
                                result = R.id.radio_dark;
                            }
                            radioGroup.check(result);
                        }));
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int i) {
        int selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (i == R.id.radio_light) {
            selectedTheme = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (i == R.id.radio_dark) {
            selectedTheme = AppCompatDelegate.MODE_NIGHT_YES;
        }
        mSettingsSharedPreferences.setSelectedTheme(selectedTheme);
    }
}
