package m.co.rh.id.a_personal_stuff.settings.ui.component;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import m.co.rh.id.a_personal_stuff.base.BaseApplication;
import m.co.rh.id.a_personal_stuff.settings.R;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class VersionMenuSV extends StatefulView<Activity> {

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_version, container, false);
        TextView textVersion = view.findViewById(R.id.text_version);
        Provider provider = BaseApplication.of(activity).getProvider();
        PackageInfo pInfo;
        String version = "unknown";//Version Name
        int verCode = -1;//Version Code
        try {
            pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            version = pInfo.versionName;//Version Name
            verCode = pInfo.versionCode;//Version Code
        } catch (PackageManager.NameNotFoundException e) {
            provider.get(ILogger.class).e("VersionMenuSV", e.getMessage(), e);
        }
        textVersion.setText(version + "+" +
                verCode);
        return view;
    }
}
