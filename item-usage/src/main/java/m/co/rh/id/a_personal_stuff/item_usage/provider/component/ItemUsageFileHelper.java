package m.co.rh.id.a_personal_stuff.item_usage.provider.component;

import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class ItemUsageFileHelper {
    private static final String TAG = ItemUsageFileHelper.class.getName();

    private final ExecutorService mExecutorService;
    private final ILogger mLogger;
    private final FileHelper mFileHelper;
    private final ItemUsageDao mItemUsageDao;

    private File mItemUsageImageParent;
    private File mItemUsageImageThumbnailParent;

    public ItemUsageFileHelper(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);

        File fileDir = provider.getContext().getFilesDir();
        mItemUsageImageParent = new File(fileDir, Constants.FILE_DIR_ITEM_USAGE_IMAGE);
        mItemUsageImageParent.mkdirs();
        mItemUsageImageThumbnailParent = new File(fileDir, Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL);
        mItemUsageImageThumbnailParent.mkdirs();

        cleanUp();
    }

    private void cleanUp() {
        Future<List<String>> itemUsageImageFileList = mExecutorService.submit(
                () -> {
                    File imageParent = getItemUsageImageParent();
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
                            List<String> imageNames = itemUsageImageFileList.get();
                            if (!imageNames.isEmpty()) {
                                for (String imageName : imageNames) {
                                    ItemUsageImage itemUsageImage = mItemUsageDao.findItemUsageImageByFileName(imageName);
                                    if (itemUsageImage == null) {
                                        deleteItemUsageImage(imageName);
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


    public File createItemUsageImage(Uri inUri, String fileName) throws IOException {
        File outFile = new File(mItemUsageImageParent, fileName);
        try {
            outFile.createNewFile();
            mFileHelper.copyImage(inUri, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getItemUsageImage(String fileName) {
        return new File(mItemUsageImageParent, fileName);
    }

    public File createItemUsageImageThumbnail(Uri content, String fileName) throws IOException {
        File outFile = new File(mItemUsageImageThumbnailParent, fileName);
        try {
            outFile.createNewFile();
            mFileHelper.copyImage(content, outFile, 320, 180);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getItemUsageImageParent() {
        return mItemUsageImageParent;
    }

    public void deleteItemUsageImage(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            File file = new File(mItemUsageImageParent, fileName);
            file.delete();
            File thumbnail = new File(mItemUsageImageThumbnailParent, fileName);
            thumbnail.delete();
        }
    }
}
