package m.co.rh.id.a_personal_stuff.base.ui.page.common;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_personal_stuff.base.R;
import m.co.rh.id.a_personal_stuff.base.util.UiUtils;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnBackPressed;

public class ImageViewPage extends StatefulView<Activity>
        implements View.OnClickListener, NavOnBackPressed<Activity> {

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;

    /**
     * The full set of images being viewed, plus the one currently shown.
     * Mutable across prev/next navigation but serialized with the nav args so
     * the viewer survives config changes / process restore.
     */
    private ArrayList<File> mFiles;
    private int mCurrentIndex;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.page_imageview, container, false);
        Button backButton = rootLayout.findViewById(R.id.button_back);
        backButton.setOnClickListener(this);
        Button shareButton = rootLayout.findViewById(R.id.button_share);
        shareButton.setOnClickListener(this);
        Button prevButton = rootLayout.findViewById(R.id.button_prev_image);
        prevButton.setOnClickListener(this);
        Button nextButton = rootLayout.findViewById(R.id.button_next_image);
        nextButton.setOnClickListener(this);
        TextView imagePositionText = rootLayout.findViewById(R.id.text_image_position);
        ImageView imageView = rootLayout.findViewById(R.id.image);

        // Load the file list + start index from the nav args. Falls back to a
        // single-file list when the legacy withFile(...) path was used.
        Args args = Args.of(mNavRoute);
        if (args != null) {
            if (args.files != null && !args.files.isEmpty()) {
                mFiles = new ArrayList<>(args.files);
                mCurrentIndex = Math.min(Math.max(args.currentIndex, 0), mFiles.size() - 1);
            } else if (args.file != null) {
                mFiles = new ArrayList<>();
                mFiles.add(args.file);
                mCurrentIndex = 0;
            }
        }
        if (mFiles == null) {
            mFiles = new ArrayList<>();
            mCurrentIndex = 0;
        }

        // Reveal the paging controls only when there is more than one image to
        // page through; the single-image case stays exactly as before. The
        // views default to "gone" in the layout, so the multi-image case must
        // explicitly show them.
        boolean multi = mFiles.size() > 1;
        int visibility = multi ? View.VISIBLE : View.GONE;
        prevButton.setVisibility(visibility);
        nextButton.setVisibility(visibility);
        imagePositionText.setVisibility(visibility);

        render(imageView, imagePositionText);
        // Bind the shared-element transition name to the entry image only. This
        // matches the transition name set on the launching thumbnail, so both
        // the enter and the return (back) animations resolve correctly. It is
        // deliberately left fixed across prev/next paging — see render().
        File entryFile = getCurrentFile();
        if (entryFile != null) {
            imageView.setTransitionName(Uri.fromFile(entryFile).toString());
        }
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_back) {
            popWithResult();
        } else if (id == R.id.button_share) {
            Context context = view.getContext();
            UiUtils.shareFile(context, getCurrentFile(), context.getString(R.string.share_image), "image/*");
        } else if (id == R.id.button_prev_image) {
            int prevIdx = mCurrentIndex - 1;
            if (prevIdx < 0) {
                prevIdx = mFiles.size() - 1;
            }
            mCurrentIndex = prevIdx;
            render((ImageView) view.getRootView().findViewById(R.id.image),
                    (TextView) view.getRootView().findViewById(R.id.text_image_position));
        } else if (id == R.id.button_next_image) {
            int nextIdx = mCurrentIndex + 1;
            if (nextIdx >= mFiles.size()) {
                nextIdx = 0;
            }
            mCurrentIndex = nextIdx;
            render((ImageView) view.getRootView().findViewById(R.id.image),
                    (TextView) view.getRootView().findViewById(R.id.text_image_position));
        }
    }

    @Override
    public void onBackPressed(View currentView, Activity activity, INavigator navigator) {
        // Handle the device back button/gesture the same way as the on-screen
        // Back button: return the index the user ended on. Without this,
        // a-navigator would pop with a null result (see Navigator.onBackPressed)
        // and the launcher's index sync would never fire.
        popWithResult();
    }

    /**
     * Pop the viewer, returning the index the user ended on so a launcher that
     * cares (the inline ImageSV editor) can sync to it. Launchers that don't
     * care (the item list) pass a null pop callback and simply ignore it.
     */
    private void popWithResult() {
        mNavigator.pop(mCurrentIndex);
    }

    /**
     * Apply the current index to the image view and position counter.
     * NOTE: the shared-element transition name is intentionally NOT updated
     * here. It is set once in {@link #createView} from the entry image, so it
     * keeps matching the source thumbnail (list row / inline image) that
     * launched the viewer. Reassigning it on every prev/next would break the
     * return animation and leave the source thumbnail blank.
     */
    private void render(ImageView imageView, TextView imagePositionText) {
        File file = getCurrentFile();
        if (file != null) {
            imageView.setImageURI(Uri.fromFile(file));
        } else {
            imageView.setImageURI(null);
        }
        if (imagePositionText != null && mFiles.size() > 1) {
            imagePositionText.setText((mCurrentIndex + 1) + "/" + mFiles.size());
        }
    }

    private File getCurrentFile() {
        if (mFiles != null && !mFiles.isEmpty() && mCurrentIndex >= 0 && mCurrentIndex < mFiles.size()) {
            return mFiles.get(mCurrentIndex);
        }
        return null;
    }

    public static class Args implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Legacy single-file field. Still populated by {@link #withFile(File)}
         * and honored when {@link #files} is absent, so existing call sites and
         * any previously serialized nav args keep working.
         */
        private File file;

        private ArrayList<File> files;
        private int currentIndex;

        public static Args withFile(File file) {
            Args args = new Args();
            args.file = file;
            return args;
        }

        public static Args withFiles(List<File> files, int currentIndex) {
            Args args = new Args();
            if (files != null) {
                args.files = new ArrayList<>(files);
                args.currentIndex = currentIndex;
            }
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
    }
}
