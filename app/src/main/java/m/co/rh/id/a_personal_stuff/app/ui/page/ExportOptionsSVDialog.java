package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.Serializable;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavRoute;

/**
 * Pre-export options dialog (StatefulViewDialog, same pattern as the
 * library's {@link m.co.rh.id.anavigator.extension.dialog.ui.ProgressSVDialog}):
 * shows the export's title plus the "Include archived items" checkbox and
 * pops with a Boolean (true = include archived) when confirmed. Cancel
 * (button, BACK or outside tap) pops with a null result, which the caller
 * must treat as cancel. Default is UNCHECKED: archived items stay out of
 * exports unless explicitly requested.
 * <p>
 * Pushed via Routes.COMMON_EXPORT_OPTIONS_DIALOG (same named-route pattern
 * as the a-navigator-extension-dialog dialogs and the base InputSVDialog).
 */
public class ExportOptionsSVDialog extends StatefulViewDialog<Activity> implements RequireNavRoute {

    @NavInject
    private transient NavRoute mNavRoute;
    @NavInject
    private transient INavigator mNavigator;

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater()
                .inflate(R.layout.page_export_options, container, false);
        TextView textTitle = rootLayout.findViewById(R.id.text_title);
        MaterialCheckBox checkBoxIncludeArchived =
                rootLayout.findViewById(R.id.checkbox_include_archived);
        MaterialButton buttonCancel = rootLayout.findViewById(R.id.button_cancel);
        MaterialButton buttonExport = rootLayout.findViewById(R.id.button_export);
        Args args = Args.of(mNavRoute);
        if (args != null && args.mTitle != null) {
            textTitle.setText(args.mTitle);
        } else {
            textTitle.setVisibility(View.GONE);
        }
        buttonCancel.setOnClickListener(view -> mNavigator.pop(null));
        buttonExport.setOnClickListener(view ->
                mNavigator.pop(checkBoxIncludeArchived.isChecked()));
        return rootLayout;
    }

    public static class Args implements Serializable {
        public static Args newArgs(String title) {
            Args args = new Args();
            args.mTitle = title;
            return args;
        }

        private static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable serializable = navRoute.getRouteArgs();
                if (serializable instanceof Args) {
                    return (Args) serializable;
                }
            }
            return null;
        }

        private String mTitle;
    }
}
