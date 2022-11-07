package m.co.rh.id.a_personal_stuff.base.provider.component;

import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class ItemFileHelper {
    private static final String TAG = ItemFileHelper.class.getName();

    private final ExecutorService mExecutorService;
    private final ILogger mLogger;
    private final FileHelper mFileHelper;
    private final ItemDao mItemDao;

    private File mItemImageParent;
    private File mItemImageThumbnailParent;

    public ItemFileHelper(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mItemDao = provider.get(ItemDao.class);

        File fileDir = provider.getContext().getFilesDir();
        mItemImageParent = new File(fileDir, Constants.FILE_DIR_ITEM_IMAGE);
        mItemImageParent.mkdirs();
        mItemImageThumbnailParent = new File(fileDir, Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL);
        mItemImageThumbnailParent.mkdirs();

        cleanUp();
    }

    private void cleanUp() {
        Future<List<String>> itemImageFileList = mExecutorService.submit(
                () -> {
                    File imageParent = getItemImageParent();
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
                            List<String> imageNames = itemImageFileList.get();
                            if (!imageNames.isEmpty()) {
                                for (String imageName : imageNames) {
                                    ItemImage itemImage = mItemDao.findItemImageByFileName(imageName);
                                    if (itemImage == null) {
                                        deleteItemImage(imageName);
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

    public File createItemImage(Uri inUri, String fileName) throws IOException {
        File outFile = new File(mItemImageParent, fileName);
        try {
            outFile.createNewFile();
            mFileHelper.copyImage(inUri, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getItemImage(String fileName) {
        return new File(mItemImageParent, fileName);
    }

    public File createItemImageThumbnail(Uri content, String fileName) throws IOException {
        File outFile = new File(mItemImageThumbnailParent, fileName);
        try {
            outFile.createNewFile();
            mFileHelper.copyImage(content, outFile, 320, 180);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getItemImageThumbnail(String fileName) {
        return new File(mItemImageThumbnailParent, fileName);
    }

    public File getItemImageParent() {
        return mItemImageParent;
    }

    public void deleteItemImage(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            File file = new File(mItemImageParent, fileName);
            file.delete();
            File thumbnail = new File(mItemImageThumbnailParent, fileName);
            thumbnail.delete();
        }
    }
}
