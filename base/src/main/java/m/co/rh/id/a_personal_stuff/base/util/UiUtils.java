package m.co.rh.id.a_personal_stuff.base.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.core.content.FileProvider;

import java.io.File;

import m.co.rh.id.a_personal_stuff.base.BaseApplication;
import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.alogger.ILogger;

public class UiUtils {
    public static void shareFile(Context context, File file, String chooserMessage) {
        shareFile(context, file, chooserMessage, "*/*");
    }

    public static void shareFile(Context context, File file, String chooserMessage, String mime) {
        Uri fileUri =
                FileProvider.getUriForFile(
                        context,
                        Constants.FILE_PROVIDER_AUTHORITY,
                        file);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.setType(mime);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent = Intent.createChooser(shareIntent, chooserMessage);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(shareIntent);
    }

    /**
     * Opens the provided web URL in an external app (e.g. a browser).
     * Returns true when an application handled the intent, false when
     * none was found (which is logged as an error).
     * The logger is resolved internally from the application's provider
     * via the given context.
     */
    public static boolean openWebUrl(Context context,
                                     String tag, String url, String errorLogMessage) {
        ILogger logger = BaseApplication.of(context).getProvider().get(ILogger.class);
        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            context.startActivity(webIntent);
            return true;
        } catch (ActivityNotFoundException e) {
            logger.e(tag, errorLogMessage, e);
            return false;
        }
    }

    public static Activity getActivity(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private UiUtils() {
    }
}
