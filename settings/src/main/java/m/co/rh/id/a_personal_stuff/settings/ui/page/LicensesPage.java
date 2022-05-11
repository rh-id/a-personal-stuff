package m.co.rh.id.a_personal_stuff.settings.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import m.co.rh.id.a_personal_stuff.settings.R;
import m.co.rh.id.anavigator.StatefulView;

public class LicensesPage extends StatefulView<Activity> {

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_licenses,
                container, false);
        WebView webView = rootLayout.findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(false);
        webView.loadUrl("file:///android_asset/licenses.html");
        return rootLayout;
    }
}
