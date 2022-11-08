package m.co.rh.id.a_personal_stuff.base.ui.page.common;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import java.io.File;
import java.io.Serializable;

import m.co.rh.id.a_personal_stuff.base.R;
import m.co.rh.id.a_personal_stuff.base.util.UiUtils;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;

public class ImageViewPage extends StatefulView<Activity> implements View.OnClickListener {

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.page_imageview, container, false);
        Button backButton = rootLayout.findViewById(R.id.button_back);
        backButton.setOnClickListener(this);
        Button shareButton = rootLayout.findViewById(R.id.button_share);
        shareButton.setOnClickListener(this);
        ImageView imageView = rootLayout.findViewById(R.id.image);
        Uri uri = getFileUri();
        imageView.setImageURI(uri);
        imageView.setTransitionName(uri.toString());
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_back) {
            mNavigator.pop();
        } else if (id == R.id.button_share) {
            Context context = view.getContext();
            UiUtils.shareFile(context, getFile(), context.getString(R.string.share_image), "image/*");
        }
    }

    private File getFile() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.file;
        }
        return null;
    }

    private Uri getFileUri() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return Uri.fromFile(args.file);
        }
        return Uri.EMPTY;
    }

    public static class Args implements Serializable {
        public static Args withFile(File file) {
            Args args = new Args();
            args.file = file;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private File file;
    }
}
