package m.co.rh.id.a_personal_stuff.settings.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import m.co.rh.id.a_personal_stuff.base.BaseApplication;
import m.co.rh.id.a_personal_stuff.base.provider.component.IBuildConfigInfo;
import m.co.rh.id.a_personal_stuff.settings.R;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class VersionMenuSV extends StatefulView<Activity> {

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_version, container, false);
        TextView textVersion = view.findViewById(R.id.text_version);
        Provider provider = BaseApplication.of(activity).getProvider();
        IBuildConfigInfo iBuildConfigInfo = provider.get(IBuildConfigInfo.class);
        textVersion.setText(iBuildConfigInfo.getVersionName() + "+" +
                iBuildConfigInfo.getVersionCode());
        return view;
    }
}
