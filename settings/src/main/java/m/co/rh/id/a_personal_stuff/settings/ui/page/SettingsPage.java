package m.co.rh.id.a_personal_stuff.settings.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;

import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.settings.R;
import m.co.rh.id.a_personal_stuff.settings.ui.component.LicensesMenuSV;
import m.co.rh.id.a_personal_stuff.settings.ui.component.LogMenuSV;
import m.co.rh.id.a_personal_stuff.settings.ui.component.ThemeMenuSV;
import m.co.rh.id.a_personal_stuff.settings.ui.component.VersionMenuSV;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SettingsPage extends StatefulView<Activity> {

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ArrayList<StatefulView> mStatefulViews;

    public SettingsPage() {
        mAppBarSV = new AppBarSV();
        mStatefulViews = new ArrayList<>();
        ThemeMenuSV themeMenuSV = new ThemeMenuSV();
        mStatefulViews.add(themeMenuSV);
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
    }
}
