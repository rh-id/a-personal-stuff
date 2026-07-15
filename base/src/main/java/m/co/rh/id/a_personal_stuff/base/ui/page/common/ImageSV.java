package m.co.rh.id.a_personal_stuff.base.ui.page.common;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import co.rh.id.lib.rx3_utils.subject.SerialPublishSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.base.R;
import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.RouteOptions;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class ImageSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, NavOnActivityResult<Activity>, NavOnRequestPermissionResult<Activity>, View.OnClickListener {
    private static final String TAG = ImageSV.class.getName();

    private static final int REQUEST_CODE_IMAGE_BROWSE = 1;
    private static final int REQUEST_CODE_TAKE_PHOTO = 2;
    private static final int REQUEST_CODE_PERMISSION_ACCESS_CAMERA = 1;

    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient FileHelper mFileHelper;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;

    private SerialBehaviorSubject<ImageState> mImageState;
    private SerialPublishSubject<File> mDeletedFile;
    private SerialPublishSubject<File> mAddFile;
    private File mTempCameraFile;

    public ImageSV() {
        mImageState = new SerialBehaviorSubject<>(new ImageState(new ArrayList<>(), 0));
        mDeletedFile = new SerialPublishSubject<>();
        mAddFile = new SerialPublishSubject<>();
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mFileHelper = mSvProvider.get(FileHelper.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.sv_image, container, false);
        Button addImageButton = rootLayout.findViewById(R.id.button_add_image);
        addImageButton.setOnClickListener(this);
        Button addPhotoButton = rootLayout.findViewById(R.id.button_add_photo);
        addPhotoButton.setOnClickListener(this);
        Button prevImageButton = rootLayout.findViewById(R.id.button_prev_image);
        prevImageButton.setOnClickListener(this);
        Button nextImageButton = rootLayout.findViewById(R.id.button_next_image);
        nextImageButton.setOnClickListener(this);
        Button deleteImageButton = rootLayout.findViewById(R.id.button_delete_image);
        deleteImageButton.setOnClickListener(this);
        TextView imagePositionText = rootLayout.findViewById(R.id.text_image_position);
        ImageView imageView = rootLayout.findViewById(R.id.imageView);
        imageView.setOnClickListener(this);
        ViewGroup imageViewContainer = rootLayout.findViewById(R.id.container_image_view);
        mRxDisposer.add("createView_onImageStateChanged",
                mImageState.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(imageState -> {
                            if (!imageState.files.isEmpty()) {
                                imagePositionText.setText((imageState.currentIndex + 1) + "/" + imageState.files.size());
                                setImage(imageView, imageState.files.get(imageState.currentIndex));
                                imageViewContainer.setVisibility(View.VISIBLE);
                            } else {
                                imageViewContainer.setVisibility(View.GONE);
                            }
                        }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_add_image) {
            Activity activity = mNavigator.getActivity();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            activity.startActivityForResult(intent, REQUEST_CODE_IMAGE_BROWSE);
        } else if (id == R.id.button_add_photo) {
            Activity activity = mNavigator.getActivity();
            if (checkCameraPermission()) {
                addPhoto(activity);
            } else {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA},
                        REQUEST_CODE_PERMISSION_ACCESS_CAMERA);
            }
        } else if (id == R.id.button_prev_image) {
            ImageState current = mImageState.getValue();
            int prevIdx = current.currentIndex - 1;
            if (prevIdx < 0) {
                prevIdx = current.files.size() - 1;
            }
            mImageState.onNext(new ImageState(current.files, prevIdx));
        } else if (id == R.id.button_next_image) {
            ImageState current = mImageState.getValue();
            int nextIdx = current.currentIndex + 1;
            if (nextIdx >= current.files.size()) {
                nextIdx = 0;
            }
            mImageState.onNext(new ImageState(current.files, nextIdx));
        } else if (id == R.id.button_delete_image) {
            ImageState current = mImageState.getValue();
            int index = current.currentIndex;
            ArrayList<File> files = new ArrayList<>(current.files);
            File file = files.remove(index);
            int position = index - 1;
            if (position < 0) {
                position = 0;
            }
            mImageState.onNext(new ImageState(files, position));
            mDeletedFile.onNext(file);
        } else if (id == R.id.imageView) {
            ImageState current = mImageState.getValue();
            File file = current.files.get(current.currentIndex);
            mNavigator.push(Routes.COMMON_IMAGEVIEW,
                    ImageViewPage.Args.withFile(file), null,
                    RouteOptions.withTransition(R.transition.page_imageview_enter,
                            R.transition.page_imageview_exit));
        }
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator navigator, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_IMAGE_BROWSE && resultCode == Activity.RESULT_OK) {
            Uri fullPhotoUri = data.getData();
            imageRequestResult(fullPhotoUri);
        } else if (requestCode == REQUEST_CODE_TAKE_PHOTO && resultCode == Activity.RESULT_OK) {
            Uri fullPhotoUri = Uri.fromFile(mTempCameraFile);
            imageRequestResult(fullPhotoUri);
            mTempCameraFile = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity, INavigator INavigator, int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION_ACCESS_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                addPhoto(activity);
            } else {
                mLogger.i(TAG, activity.getString(m.co.rh.id.a_personal_stuff.base.
                        R.string.error_permission_denied));
            }
        }
    }

    private void imageRequestResult(Uri fullPhotoUri) {
        mRxDisposer.add("imageRequestResult",
                Single.fromCallable(() -> mFileHelper.createImageTempFile(fullPhotoUri))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((imageFile, throwable) -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause();
                                if (cause == null) cause = throwable;
                                mLogger.e(TAG, cause.getMessage(), cause);
                            } else {
                                mAddFile.onNext(imageFile);
                            }
                        })
        );
    }

    public void setImageFiles(Collection<File> files) {
        ArrayList<File> fileList = (files != null && !files.isEmpty())
                ? new ArrayList<>(files) : new ArrayList<>();
        ImageState current = mImageState.getValue();
        int index = Math.min(current.currentIndex, Math.max(0, fileList.size() - 1));
        mImageState.onNext(new ImageState(fileList, index));
    }

    public Subject<File> getDeletedFileSubject() {
        return mDeletedFile.getSubject();
    }

    public Subject<File> getAddFileSubject() {
        return mAddFile.getSubject();
    }

    public void addImage(File file) {
        ImageState current = mImageState.getValue();
        ArrayList<File> files = new ArrayList<>(current.files);
        files.add(file);
        mImageState.onNext(new ImageState(files, files.size() - 1));
    }

    private void setImage(ImageView imageView, File file) {
        if (file != null) {
            Uri uri = Uri.fromFile(file);
            imageView.setImageURI(uri);
            imageView.setTransitionName(uri.toString());
        } else {
            imageView.setImageURI(null);
        }
    }

    private void addPhoto(Activity activity) {
        try {
            mTempCameraFile = mFileHelper.createImageTempFile();
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Uri photoURI = FileProvider.getUriForFile(activity,
                    Constants.FILE_PROVIDER_AUTHORITY,
                    mTempCameraFile);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            activity.startActivityForResult(cameraIntent, REQUEST_CODE_TAKE_PHOTO);
        } catch (Exception e) {
            mLogger.e(TAG, e.getMessage(), e);
        }
    }

    private boolean checkCameraPermission() {
        Context context = mSvProvider.getContext();
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static class ImageState implements Serializable {
        final ArrayList<File> files;
        final int currentIndex;

        ImageState(ArrayList<File> files, int currentIndex) {
            this.files = files;
            this.currentIndex = currentIndex;
        }
    }
}
