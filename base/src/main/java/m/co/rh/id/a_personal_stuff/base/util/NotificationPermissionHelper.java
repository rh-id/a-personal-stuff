package m.co.rh.id.a_personal_stuff.base.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;

/**
 * Helper for checking notification posting permission.
 */
public final class NotificationPermissionHelper {

    private NotificationPermissionHelper() {
    }

    /**
     * POST_NOTIFICATIONS only exists/is enforced on API 33+; on older platforms posting is always allowed.
     */
    public static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
