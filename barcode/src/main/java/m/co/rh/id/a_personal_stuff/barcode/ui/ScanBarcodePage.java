package m.co.rh.id.a_personal_stuff.barcode.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_personal_stuff.barcode.R;
import m.co.rh.id.a_personal_stuff.barcode.ui.component.ScanBarcodePreview;
import m.co.rh.id.a_personal_stuff.base.BaseApplication;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavActivityLifecycle;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

@SuppressWarnings("deprecation")
class ScanBarcodePage extends StatefulView<Activity> implements RequireNavigator, NavActivityLifecycle<Activity>, NavOnRequestPermissionResult<Activity>, View.OnClickListener {
    private static final int REQUEST_CODE_PERMISSION_ACCESS_CAMERA = 1;
    private static final String TAG = ScanBarcodePage.class.getName();

    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;

    private SerialBehaviorSubject<Boolean> mCanAccessCamera;
    private transient BehaviorSubject<com.google.zxing.Result> mBarcodeResult;
    private transient ScanBarcodePreview mScanBarcodePreview;

    @Override
    public void provideNavigator(INavigator navigator) {
        Activity activity = navigator.getActivity();
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        Provider provider = BaseApplication.of(activity).getProvider();
        mNavigator = navigator;
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);

        mBarcodeResult = BehaviorSubject.create();
        boolean canAccessCamera = canAccessCamera();
        if (mCanAccessCamera == null) {
            mCanAccessCamera = new SerialBehaviorSubject<>(canAccessCamera);
        } else {
            mCanAccessCamera.onNext(canAccessCamera);
        }
        if (!canAccessCamera && !checkCameraPermission()) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE_PERMISSION_ACCESS_CAMERA);
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_scan_barcode, container, false);
        Button backButton = rootLayout.findViewById(R.id.button_back);
        backButton.setOnClickListener(this);
        Button flashButton = rootLayout.findViewById(R.id.button_flash);
        flashButton.setOnClickListener(this);
        ViewGroup previewContainer = rootLayout.findViewById(R.id.container_preview);
        TextView noAccessText = rootLayout.findViewById(R.id.text_no_access);
        mRxDisposer.add("createView_onAccessCameraChanged",
                mCanAccessCamera.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            if (aBoolean) {
                                noAccessText.setVisibility(View.GONE);
                                previewContainer.setVisibility(View.VISIBLE);
                                if (mScanBarcodePreview == null) {
                                    initScanBarcodePreview();
                                } else {
                                    mScanBarcodePreview.dispose();
                                    initScanBarcodePreview();
                                }
                                previewContainer.addView(mScanBarcodePreview);
                            } else {
                                noAccessText.setVisibility(View.VISIBLE);
                                previewContainer.setVisibility(View.GONE);
                                previewContainer.removeAllViews();
                                if (mScanBarcodePreview != null) {
                                    mScanBarcodePreview.dispose();
                                    mScanBarcodePreview = null;
                                }
                            }
                        }));
        mRxDisposer.add("createView_onBarcodeResultChanged",
                mBarcodeResult.observeOn(AndroidSchedulers.mainThread())
                        .take(1)
                        .subscribe(result -> mNavigator.pop(Result.with(result)))
        );
        return rootLayout;
    }

    private void initScanBarcodePreview() {
        Camera camera = Camera.open();
        mScanBarcodePreview = new ScanBarcodePreview(mSvProvider.getContext(), camera, mSvProvider);
        mRxDisposer.add("provideNavigator_onBarcodeResult",
                mScanBarcodePreview.getBarcodeResultFlow().observeOn(Schedulers.from(mExecutorService))
                        .subscribe(result -> mBarcodeResult.onNext(result)));
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mScanBarcodePreview != null) {
            mScanBarcodePreview.dispose();
            mScanBarcodePreview = null;
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_back) {
            mNavigator.pop();
        } else if (id == R.id.button_flash) {
            if (mScanBarcodePreview != null) {
                mScanBarcodePreview.toggleFlash();
            }
        }
    }

    private boolean canAccessCamera() {
        return checkCameraHardware() && checkCameraPermission();
    }

    private boolean checkCameraHardware() {
        Context context = mSvProvider.getContext();
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    private boolean checkCameraPermission() {
        Context context = mSvProvider.getContext();
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onNavActivityResumed(Activity activity) {
        boolean canAccessCamera = canAccessCamera();
        if (!canAccessCamera && !checkCameraPermission()) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CODE_PERMISSION_ACCESS_CAMERA);
        } else {
            mCanAccessCamera.onNext(canAccessCamera);
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    @Override
    public void onNavActivityPaused(Activity activity) {
        mCanAccessCamera.onNext(false);
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity, INavigator INavigator, int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION_ACCESS_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mCanAccessCamera.onNext(true);
            } else {
                mLogger.i(TAG, activity.getString(m.co.rh.id.a_personal_stuff.base.
                        R.string.error_permission_denied));
            }
        }
    }

    static class Result implements Serializable {
        static Result with(com.google.zxing.Result barcodeResult) {
            Result result = new Result();
            result.barcode = barcodeResult.getText();
            return result;
        }

        private String barcode;

        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable result = navRoute.getRouteResult();
                if (result instanceof Result) {
                    return (Result) result;
                }
            }
            return null;
        }

        public String getBarcode() {
            return barcode;
        }
    }
}
