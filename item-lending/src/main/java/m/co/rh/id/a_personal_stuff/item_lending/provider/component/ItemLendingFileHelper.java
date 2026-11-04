package m.co.rh.id.a_personal_stuff.item_lending.provider.component;

import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLendingImage;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class ItemLendingFileHelper {
    private static final String TAG = ItemLendingFileHelper.class.getName();

    private final ExecutorService mExecutorService;
    private final ILogger mLogger;
    private final FileHelper mFileHelper;
    private final ItemLendingDao mItemLendingDao;

    private File mItemLendingImageParent;
    private File mItemLendingImageThumbnailParent;

    public ItemLendingFileHelper(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mItemLendingDao = provider.get(ItemLendingDao.class);

        File fileDir = provider.getContext().getFilesDir();
        mItemLendingImageParent = new File(fileDir, Constants.FILE_DIR_ITEM_LENDING_IMAGE);
        mItemLendingImageParent.mkdirs();
        mItemLendingImageThumbnailParent = new File(fileDir, Constants.FILE_DIR_ITEM_LENDING_IMAGE_THUMBNAIL);
        mItemLendingImageThumbnailParent.mkdirs();

        cleanUp();
    }

    protected void cleanUp() {
        Future<List<String>> itemLendingImageFileList = mExecutorService.submit(
                () -> {
                    File imageParent = getItemLendingImageParent();
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
                            List<String> imageNames = itemLendingImageFileList.get();
                            if (!imageNames.isEmpty()) {
                                for (String imageName : imageNames) {
                                    ItemLendingImage itemLendingImage = mItemLendingDao.findItemLendingImageByFileName(imageName);
                                    if (itemLendingImage == null) {
                                        deleteItemLendingImage(imageName);
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


    public File createItemLendingImage(Uri inUri, String fileName) throws IOException {
        File outFile = new File(mItemLendingImageParent, fileName);
        try {
            outFile.createNewFile();
            mFileHelper.copyImage(inUri, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getItemLendingImage(String fileName) {
        return new File(mItemLendingImageParent, fileName);
    }

    public File getItemLendingImageThumbnail(String fileName) {
        return new File(mItemLendingImageThumbnailParent, fileName);
    }

    public File createItemLendingImageThumbnail(Uri content, String fileName) throws IOException {
        File outFile = new File(mItemLendingImageThumbnailParent, fileName);
        try {
            outFile.createNewFile();
            mFileHelper.copyImage(content, outFile, 320, 180);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getItemLendingImageParent() {
        return mItemLendingImageParent;
    }

    public File getItemLendingImageThumbnailParent() {
        return mItemLendingImageThumbnailParent;
    }

    public void deleteItemLendingImage(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            File file = new File(mItemLendingImageParent, fileName);
            file.delete();
            File thumbnail = new File(mItemLendingImageThumbnailParent, fileName);
            thumbnail.delete();
        }
    }
}
