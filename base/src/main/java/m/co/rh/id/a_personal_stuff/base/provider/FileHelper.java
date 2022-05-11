package m.co.rh.id.a_personal_stuff.base.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Class to provide files through this app
 */
public class FileHelper {
    private static final String TAG = FileHelper.class.getName();

    private Context mAppContext;
    private ProviderValue<ILogger> mLogger;
    private File mLogFile;
    private File mTempFileRoot;

    public FileHelper(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        File cacheDir = mAppContext.getCacheDir();
        File fileDir = mAppContext.getFilesDir();
        mLogFile = new File(cacheDir, "alogger/app.log");
        mTempFileRoot = new File(cacheDir, "/tmp");
        mTempFileRoot.mkdirs();
    }

    public File createTempFile(String fileName) throws IOException {
        return createTempFile(fileName, null);
    }

    /**
     * Create temporary file
     *
     * @param fileName file name for this file
     * @param content  content of the file to write to this temp file
     * @return temporary file
     * @throws IOException when failed to create file
     */
    public File createTempFile(String fileName, Uri content) throws IOException {
        File parent = new File(mTempFileRoot, UUID.randomUUID().toString());
        parent.mkdirs();
        String fName = fileName;
        if (fName == null || fName.isEmpty()) {
            fName = UUID.randomUUID().toString();
        }
        File tmpFile = new File(parent, fName);
        tmpFile.createNewFile();

        if (content != null) {
            ContentResolver cr = mAppContext.getContentResolver();
            InputStream inputStream = cr.openInputStream(content);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

            FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            byte[] buff = new byte[2048];
            int b = bufferedInputStream.read(buff);
            while (b != -1) {
                bufferedOutputStream.write(buff);
                b = bufferedInputStream.read(buff);
            }
            bufferedOutputStream.close();
            fileOutputStream.close();
            bufferedInputStream.close();
            inputStream.close();
        }
        return tmpFile;
    }

    public void clearLogFile() {
        if (mLogFile.exists()) {
            mLogFile.delete();
            try {
                mLogFile.createNewFile();
            } catch (Throwable throwable) {
                mLogger.get().e(TAG, "Failed to create new file for log", throwable);
            }
        }
    }

    public File getLogFile() {
        return mLogFile;
    }

    public File createImageTempFile() throws IOException {
        File parent = new File(mTempFileRoot, UUID.randomUUID().toString());
        parent.mkdirs();
        File tmpFile = new File(parent, UUID.randomUUID().toString() + ".jpg");
        tmpFile.createNewFile();
        return tmpFile;
    }

    public File createImageTempFile(Uri content) throws IOException {
        File outFile = createImageTempFile();
        try {
            copyImage(content, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public void copyImage(Uri content, File outFile) throws IOException {
        copyImage(content, outFile, 1280, 720);
    }

    public void copyImage(Uri content, File outFile, int width, int height) throws IOException {
        ContentResolver contentResolver = mAppContext.getContentResolver();
        FileDescriptor fd = contentResolver.openFileDescriptor(
                content, "r").getFileDescriptor();
        InputStream fis = new FileInputStream(fd);
        BitmapFactory.Options bmOptions = getBitmapOptionForCompression(fis, width, height);
        OutputStream fileOutputStream = new BufferedOutputStream(
                new FileOutputStream(outFile), 10240);
        Bitmap bitmap = processExifAttr(mAppContext, content, bmOptions);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream);
        fileOutputStream.flush();
        fileOutputStream.close();
    }

    private BitmapFactory.Options getBitmapOptionForCompression(InputStream fis, int width, int height) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(fis, null, bmOptions);
        int inWidth = bmOptions.outWidth;
        int inHeight = bmOptions.outHeight;
        int outWidth = width;
        int outHeight = height;
        if (inHeight > inWidth) {
            outHeight = width;
            outWidth = height;
        }
        int scaleFactor = Math.max(1, Math.min(inWidth / outWidth, inHeight / outHeight));
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        return bmOptions;
    }

    private Bitmap processExifAttr(Context context, Uri imageUri, BitmapFactory.Options bmOptions) throws IOException {
        ContentResolver contentResolver = context.getContentResolver();
        FileDescriptor fd = contentResolver.openFileDescriptor(
                imageUri, "r").getFileDescriptor();
        ExifInterface exifInterface = new ExifInterface(fd);
        int rotation = getRotation(exifInterface);

        // get fd again
        fd = contentResolver.openFileDescriptor(
                imageUri, "r").getFileDescriptor();
        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fd, null, bmOptions);
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.setRotate(rotation);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                    matrix, true);
        }
        return bitmap;
    }

    private int getRotation(ExifInterface exifInterface) {
        int rotation = 0;
        int exifRotation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

        if (exifRotation != ExifInterface.ORIENTATION_UNDEFINED) {
            switch (exifRotation) {
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotation = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotation = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotation = 90;
                    break;
            }
        }
        return rotation;
    }
}
