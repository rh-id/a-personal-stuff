package m.co.rh.id.a_personal_stuff.item_purchase.provider.component;

import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class ItemPurchaseFileHelper {
    private static final String TAG = ItemPurchaseFileHelper.class.getName();

    private final ExecutorService mExecutorService;
    private final ILogger mLogger;
    private final FileHelper mFileHelper;
    private final ItemPurchaseDao mItemPurchaseDao;

    private File mItemPurchaseImageParent;
    private File mItemPurchaseImageThumbnailParent;

    public ItemPurchaseFileHelper(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mLogger = provider.get(ILogger.class);
        mFileHelper = provider.get(FileHelper.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);

        File fileDir = provider.getContext().getFilesDir();
        mItemPurchaseImageParent = new File(fileDir, Constants.FILE_DIR_ITEM_PURCHASE_IMAGE);
        mItemPurchaseImageParent.mkdirs();
        mItemPurchaseImageThumbnailParent = new File(fileDir, Constants.FILE_DIR_ITEM_PURCHASE_IMAGE_THUMBNAIL);
        mItemPurchaseImageThumbnailParent.mkdirs();

        cleanUp();
    }

    protected void cleanUp() {
        Future<List<String>> itemPurchaseImageFileList = mExecutorService.submit(
                () -> {
                    File imageParent = getItemPurchaseImageParent();
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
                            List<String> imageNames = itemPurchaseImageFileList.get();
                            if (!imageNames.isEmpty()) {
                                for (String imageName : imageNames) {
                                    ItemPurchaseImage itemPurchaseImage = mItemPurchaseDao.findItemPurchaseImageByFileName(imageName);
                                    if (itemPurchaseImage == null) {
                                        deleteItemPurchaseImage(imageName);
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


    public File createItemPurchaseImage(Uri inUri, String fileName) throws IOException {
        File outFile = new File(mItemPurchaseImageParent, fileName);
        try {
            outFile.createNewFile();
            mFileHelper.copyImage(inUri, outFile);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getItemPurchaseImage(String fileName) {
        return new File(mItemPurchaseImageParent, fileName);
    }

    public File createItemPurchaseImageThumbnail(Uri content, String fileName) throws IOException {
        File outFile = new File(mItemPurchaseImageThumbnailParent, fileName);
        try {
            outFile.createNewFile();
            mFileHelper.copyImage(content, outFile, 320, 180);
            return outFile;
        } catch (Exception e) {
            outFile.delete();
            throw e;
        }
    }

    public File getItemPurchaseImageThumbnail(String fileName) {
        return new File(mItemPurchaseImageThumbnailParent, fileName);
    }

    public File getItemPurchaseImageParent() {
        return mItemPurchaseImageParent;
    }

    public File getItemPurchaseImageThumbnailParent() {
        return mItemPurchaseImageThumbnailParent;
    }

    public void deleteItemPurchaseImage(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            File file = new File(mItemPurchaseImageParent, fileName);
            file.delete();
            File thumbnail = new File(mItemPurchaseImageThumbnailParent, fileName);
            thumbnail.delete();
        }
    }
}
