package m.co.rh.id.a_personal_stuff.base.ui.page.common;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ScrollView;

import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.android.material.radiobutton.MaterialRadioButton;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;

public class SelectionPage extends StatefulViewDialog<Activity> {
    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        LinearLayoutCompat.LayoutParams layoutParams = new LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT,
                LinearLayoutCompat.LayoutParams.MATCH_PARENT
        );
        LinearLayoutCompat rootLayout = new LinearLayoutCompat(activity);
        rootLayout.setOrientation(LinearLayoutCompat.VERTICAL);
        rootLayout.setLayoutParams(layoutParams);
        rootLayout.setMinimumHeight(400);
        rootLayout.setPadding(48,48,48,48);
        ScrollView scrollView = new ScrollView(activity);
        LinearLayoutCompat linearLayout = new LinearLayoutCompat(activity);
        linearLayout.setOrientation(LinearLayoutCompat.VERTICAL);
        linearLayout.setLayoutParams(layoutParams);
        scrollView.addView(linearLayout);
        rootLayout.addView(scrollView);
        Args args = getArgs();
        if (args != null) {
            if (args.selectionStringKey != null && !args.selectionStringKey.isEmpty()) {
                int size = args.selectionStringKey.size();
                for (int i = 0; i < size; i++) {
                    final Integer popResult = i;
                    CompoundButton selectionButton = new MaterialRadioButton(activity);
                    selectionButton.setText(args.selectionStringKey.get(i));
                    selectionButton.setOnClickListener(view -> mNavigator.pop(popResult));
                    if (args.selectedIndex != null && args.selectedIndex == i) {
                        selectionButton.setChecked(true);
                    }
                    linearLayout.addView(selectionButton);
                }
            }
        }
        return rootLayout;
    }

    private Args getArgs() {
        return Args.of(mNavRoute);
    }

    public static class Args implements Serializable {
        public static Args with(Integer selectedIndex, Collection<Integer> selectedStringKey) {
            Args args = new Args();
            args.selectedIndex = selectedIndex;
            args.selectionStringKey = new ArrayList<>(selectedStringKey);
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

        private Integer selectedIndex;
        private ArrayList<Integer> selectionStringKey;
    }
}
