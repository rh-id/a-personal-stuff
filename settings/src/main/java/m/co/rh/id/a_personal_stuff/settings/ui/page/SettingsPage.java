package m.co.rh.id.a_personal_stuff.settings.ui.page;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;

import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.settings.R;
import m.co.rh.id.a_personal_stuff.settings.ui.component.AlertSettingsMenuSV;
import m.co.rh.id.a_personal_stuff.settings.ui.component.AutoBackupMenuSV;
import m.co.rh.id.a_personal_stuff.settings.ui.component.LicensesMenuSV;
import m.co.rh.id.a_personal_stuff.settings.ui.component.LogMenuSV;
import m.co.rh.id.a_personal_stuff.settings.ui.component.ThemeMenuSV;
import m.co.rh.id.a_personal_stuff.settings.ui.component.VersionMenuSV;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SettingsPage extends StatefulView<Activity> implements NavOnActivityResult<Activity> {

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ArrayList<StatefulView> mStatefulViews;
    // kept outside the list so onActivityResult can be forwarded to it
    private AutoBackupMenuSV mAutoBackupMenuSV;

    public SettingsPage() {
        mAppBarSV = new AppBarSV();
        mStatefulViews = new ArrayList<>();
        ThemeMenuSV themeMenuSV = new ThemeMenuSV();
        mStatefulViews.add(themeMenuSV);
        AlertSettingsMenuSV alertSettingsMenuSV = new AlertSettingsMenuSV();
        mStatefulViews.add(alertSettingsMenuSV);
        mAutoBackupMenuSV = new AutoBackupMenuSV();
        mStatefulViews.add(mAutoBackupMenuSV);
        LogMenuSV logMenuSV = new LogMenuSV();
        mStatefulViews.add(logMenuSV);
        LicensesMenuSV licensesMenuSV = new LicensesMenuSV();
        mStatefulViews.add(licensesMenuSV);
        VersionMenuSV versionMenuSV = new VersionMenuSV();
        mStatefulViews.add(versionMenuSV);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.page_settings, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.settings));
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        ViewGroup content = view.findViewById(R.id.content);
        for (StatefulView statefulView : mStatefulViews) {
            LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            content.addView(statefulView.buildView(activity, content), lparams);
        }
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        if (mStatefulViews != null && !mStatefulViews.isEmpty()) {
            for (StatefulView statefulView : mStatefulViews) {
                statefulView.dispose(activity);
            }
            mStatefulViews.clear();
            mStatefulViews = null;
        }
        mAutoBackupMenuSV = null;
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator navigator, int requestCode, int resultCode, Intent data) {
        // NavOnActivityResult only fires for the current top page, so forward
        // to the auto backup menu that started the folder picker
        AutoBackupMenuSV autoBackupMenuSV = mAutoBackupMenuSV;
        if (autoBackupMenuSV != null) {
            autoBackupMenuSV.onActivityResult(currentView, activity, navigator,
                    requestCode, resultCode, data);
        }
    }
}
