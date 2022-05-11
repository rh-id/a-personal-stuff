package m.co.rh.id.a_personal_stuff.app.provider.component;

import m.co.rh.id.a_personal_stuff.BuildConfig;
import m.co.rh.id.a_personal_stuff.base.provider.component.IBuildConfigInfo;

public class BuildConfigInfo implements IBuildConfigInfo {
    @Override
    public String getVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    @Override
    public int getVersionCode() {
        return BuildConfig.VERSION_CODE;
    }
}
