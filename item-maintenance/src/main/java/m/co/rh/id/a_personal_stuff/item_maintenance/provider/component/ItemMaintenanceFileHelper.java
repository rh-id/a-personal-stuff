package m.co.rh.id.a_personal_stuff.item_maintenance.provider.component;

import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class ItemMaintenanceFileHelper {
    private static final String TAG = ItemMaintenanceFileHelper.class.getName();

    private final ExecutorService mExecutorService;
    private final ILogger mLogger;
    private final FileHelper mFileHelper;
    private final ItemMaintenanceDao mItemMaintenanceDao;

    private File mItemMaintenanceImageParent;
    private File mItemMaintenanceImageThumbnailParent;

    public ItemMaintenanceFileHelper(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);

        File fileDir = provider.getContext().getFilesDir();
        mItemMaintenanceImageParent = new File(fileDir, Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE);
        mItemMaintenanceImageParent.mkdirs();
        mItemMaintenanceImageThumbnailParent = new File(fileDir, Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL);
        mItemMaintenanceImageThumbnailParent.mkdirs();

        cleanUp();
    }

    private void cleanUp() {
        Future<List<String>> itemMaintenanceImageFileList = mExecutorService.submit(
                () -> {
                    File imageParent = getItemMaintenanceImageParent();
                    File[] files = imageParent.listFiles();
                    List<String> fileNames = new ArrayList<>();
                    if (files != null && files.length > 0) {
                        for (File file : files) {
                            if (!file.isDirectory()) {
                                fileNames.add(file.getName());
                            }
                        }
                    }
                    return fileNames;
                }
        );
        mExecutorService.execute(() -> {
            try {
                List<Future<Boolean>> taskList = new ArrayList<>();
                taskList.add(
                        mExecutorService.submit(() -> {
                            List<String> imageNames = itemMaintenanceImageFileList.get();
                            if (!imageNames.isEmpty()) {
                                for (String imageName : imageNames) {
                                    ItemMaintenanceImage itemMaintenanceImage = mItemMaintenanceDao.findItemMaintenanceImageByFileName(imageName);
                                    if (itemMaintenanceImage == null) {
                                        deleteItemMaintenanceImage(imageName);
                                    }
                                }
                            }
                            return true;
                        })
                );
                for (Future<Boolean> task : taskList) {
                    task.get();
                }
            } catch (Exception e) {
                mLogger.d(TAG, "Error occurred when cleaning file", e);
            }
        });
    }

    public File createItemMaintenanceImage(Uri inUri, String fileName) throws IOException {
        File outFile = new File(mItemMaintenanceImageParent, fileName);
        try {
            outFile.createNewFile();
            mFileHelper.copyImage(inUri, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getItemMaintenanceImage(String fileName) {
        return new File(mItemMaintenanceImageParent, fileName);
    }

    public File createItemMaintenanceImageThumbnail(Uri content, String fileName) throws IOException {
        File outFile = new File(mItemMaintenanceImageThumbnailParent, fileName);
        try {
            outFile.createNewFile();
            mFileHelper.copyImage(content, outFile, 320, 180);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public void deleteItemMaintenanceImage(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            File file = new File(mItemMaintenanceImageParent, fileName);
            file.delete();
            File thumbnail = new File(mItemMaintenanceImageThumbnailParent, fileName);
            thumbnail.delete();
        }
    }

    public File getItemMaintenanceImageParent() {
        return mItemMaintenanceImageParent;
    }
}
