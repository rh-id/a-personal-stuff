package m.co.rh.id.a_personal_stuff.barcode.ui.component;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.MultiFormatOneDReader;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Why using Camera API? because Camera2 and CameraX can't do this easily.
 * Plus no need to use more library
 */
@SuppressWarnings("deprecation")
public class ScanBarcodePreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback, Camera.AutoFocusCallback {
    private static final String TAG = ScanBarcodePreview.class.getName();
    private ReentrantLock mLock;
    private ExecutorService mExecutorService;
    private ScheduledExecutorService mScheduledExecutorService;
    private ILogger mLogger;
    private SurfaceHolder mHolder;
    private Camera mCamera;
    private long mLastProcessedPreview;
    private MultiFormatOneDReader mMultiFormatOneDReader;
    private PublishSubject<Result> mBarcodeResultSubject;

    private boolean mSupportAutoFocus;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private ScheduledFuture<?> mAutoFocusTask;

    public ScanBarcodePreview(Context context, Camera camera, Provider provider) {
        super(context);
        mLock = new ReentrantLock();
        mCamera = camera;
        mExecutorService = provider.get(ExecutorService.class);
        mScheduledExecutorService = provider.get(ScheduledExecutorService.class);
        mLogger = provider.get(ILogger.class);
        Camera.Parameters parameters = mCamera.getParameters();
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            // Autofocus mode is supported
            mSupportAutoFocus = true;
        }
        parameters.setPreviewFormat(ImageFormat.NV21);
        mCamera.setParameters(parameters);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        mLastProcessedPreview = System.currentTimeMillis();
        mMultiFormatOneDReader = new MultiFormatOneDReader(null);
        mBarcodeResultSubject = PublishSubject.create();
    }


    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        mLock.lock();
        mPreviewWidth = mCamera.getParameters().getPreviewSize().width;
        mPreviewHeight = mCamera.getParameters().getPreviewSize().height;
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.startPreview();
        } catch (IOException e) {
            mLogger.d(TAG, "(surfaceCreated) Error setting camera preview: " + e.getMessage());
        }
        mLock.unlock();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        mLock.lock();
        if (mAutoFocusTask != null) {
            mAutoFocusTask.cancel(false);
        }
        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
            mLogger.d(TAG, "(surfaceChanged) Error stopping camera preview: " + e.getMessage(), e);
        }

        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();
            enableAutoFocus();
        } catch (Exception e) {
            mLogger.d(TAG, "(surfaceChanged) Error starting camera preview: " + e.getMessage(), e);
        }
        mLock.unlock();
    }

    private void enableAutoFocus() {
        if (mCamera != null && mSupportAutoFocus) {
            mCamera.autoFocus(this);
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        // Leave blank
    }

    @Override
    public void onAutoFocus(boolean b, Camera camera) {
        // repeat autofocus next 2 seconds
        mAutoFocusTask = mScheduledExecutorService.schedule(() -> {
                    mLock.lock();
                    enableAutoFocus();
                    mLock.unlock();
                }
                , 2, TimeUnit.SECONDS);
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        long currentTimeMillis = System.currentTimeMillis();
        long previousTimeMillis = mLastProcessedPreview;
        long differenceMilis = (currentTimeMillis - previousTimeMillis);
        mLogger.v(TAG, "onPreviewFrame differenceMilis:" + differenceMilis);
        // capture every 16 milisecs (more or less 60fps) to avoid out of memory error
        if (differenceMilis >= 16) {
            int width = mPreviewWidth;
            int height = mPreviewHeight;
            mExecutorService.execute(() -> {
                LuminanceSource luminanceSource = new PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false);
                Binarizer binarizer = new HybridBinarizer(luminanceSource);
                BinaryBitmap binaryBitmap = new BinaryBitmap(binarizer);
                try {
                    Result result = mMultiFormatOneDReader.decode(binaryBitmap);
                    mLogger.v(TAG, "Type: " + result.getBarcodeFormat().name() + " text: " + result.getText());
                    mBarcodeResultSubject.onNext(result);
                } catch (Exception e) {
                    mLogger.v(TAG, e.getMessage());
                }
            });
        }
    }

    public void toggleFlash() {
        mLock.lock();
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            String flashMode = parameters.getFlashMode();
            if (!Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                flashMode = Camera.Parameters.FLASH_MODE_TORCH;
            } else {
                flashMode = Camera.Parameters.FLASH_MODE_OFF;
            }
            parameters.setFlashMode(flashMode);
            mCamera.setParameters(parameters);
        }
        mLock.unlock();
    }

    // Call this to cleanup
    public void dispose() {
        mLock.lock();
        if (mAutoFocusTask != null) {
            mAutoFocusTask.cancel(true);
            mAutoFocusTask = null;
        }
        if (mCamera != null) {
            try {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);
                mCamera.release();
            } catch (Exception e) {
                // ignore: tried to stop a non-existent preview
                mLogger.d(TAG, "(dispose) Error stopping camera preview: " + e.getMessage(), e);
            } finally {
                mCamera = null;
            }
        }
        if (mHolder != null) {
            mHolder.removeCallback(this);
            mHolder = null;
        }
        mBarcodeResultSubject.onComplete();
        mLock.unlock();
    }

    public Flowable<Result> getBarcodeResultFlow() {
        return Flowable.fromObservable(mBarcodeResultSubject, BackpressureStrategy.DROP);
    }
}
