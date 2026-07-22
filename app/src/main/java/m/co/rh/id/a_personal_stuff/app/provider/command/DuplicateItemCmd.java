package m.co.rh.id.a_personal_stuff.app.provider.command;

import android.net.Uri;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.aprovider.Provider;

/**
 * Duplicate an existing {@link ItemState} into a brand-new item.
 * <p>
 * Copies the text fields, barcode, amount, price, description and expiry date,
 * plus tags. Each image file is physically copied to a new file so that the
 * duplicated item owns its own image files (deleting either item never orphans
 * the other's images). All ids/timestamps are reset so the new item is inserted
 * as new rows.
 */
public class DuplicateItemCmd {
    private final ExecutorService mExecutorService;
    private final ItemDao mItemDao;
    private final ItemChangeNotifier mItemChangeNotifier;
    private final ItemFileHelper mItemFileHelper;
    private final FileHelper mFileHelper;

    public DuplicateItemCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemDao = provider.get(ItemDao.class);
        mItemChangeNotifier = provider.get(ItemChangeNotifier.class);
        mItemFileHelper = provider.get(ItemFileHelper.class);
        mFileHelper = provider.get(FileHelper.class);
    }

    public Single<ItemState> execute(ItemState source) {
        return Single.fromCallable(() -> {
                    ItemState clone = source.cloneForDuplicate();
                    ArrayList<ItemImage> itemImages = clone.getItemImages();
                    if (itemImages != null && !itemImages.isEmpty()) {
                        for (ItemImage itemImage : itemImages) {
                            String originalFileName = itemImage.fileName;
                            if (originalFileName == null || originalFileName.isEmpty()) {
                                continue;
                            }
                            String newFileName = mFileHelper.generateImageFileName();
                            mItemFileHelper.createItemImage(
                                    Uri.fromFile(mItemFileHelper.getItemImage(originalFileName)),
                                    newFileName);
                            mItemFileHelper.createItemImageThumbnail(
                                    Uri.fromFile(mItemFileHelper.getItemImage(originalFileName)),
                                    newFileName);
                            itemImage.fileName = newFileName;
                        }
                    }
                    mItemDao.insertItem(clone);
                    mItemChangeNotifier.itemAdded(clone.clone());
                    return clone;
                }).subscribeOn(Schedulers.from(mExecutorService));
    }
}
