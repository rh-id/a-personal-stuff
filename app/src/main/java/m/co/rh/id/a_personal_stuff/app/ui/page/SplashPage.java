package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class SplashPage extends StatefulView<Activity> implements RequireNavigator {
    private transient INavigator mNavigator;

    private String mNextPage;

    public SplashPage(String nextPage) {
        mNextPage = nextPage;
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        Provider provider = BaseApplication.of(activity).getProvider();
        provider.get(Handler.class)
                .postDelayed(() ->
                        mNavigator.replace(mNextPage), 1000);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        return activity.getLayoutInflater().inflate(R.layout.page_splash, container, false);
    }

}
