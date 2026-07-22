package m.co.rh.id.a_personal_stuff.base.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;

public class ItemState implements Serializable, Cloneable {
    private SerialBehaviorSubject<Item> mItem;
    private SerialBehaviorSubject<ArrayList<ItemImage>> mItemImages;
    private SerialBehaviorSubject<TreeSet<ItemTag>> mItemTags;

    public ItemState() {
        mItem = new SerialBehaviorSubject<>(new Item());
        mItemImages = new SerialBehaviorSubject<>(new ArrayList<>());
        mItemTags = new SerialBehaviorSubject<>(new TreeSet<>());
    }

    public void updateItem(Item item) {
        mItem.onNext(item);
    }

    public Item getItem() {
        return mItem.getValue();
    }


    public Long getItemId() {
        return getItem().id;
    }

    public void setItemName(String s) {
        getItem().name = s;
    }

    public String getItemName() {
        return getItem().name;
    }

    public void setItemAmount(int amount) {
        getItem().amount = amount;
    }

    public int getItemAmount() {
        return getItem().amount;
    }

    public void updateItemExpiredDateTime(Date selectedDate) {
        Item item = getItem();
        item.expiredDateTime = selectedDate;
        updateItem(item);
    }

    public Date getItemExpiredDateTime() {
        return getItem().expiredDateTime;
    }

    public void setItemPrice(BigDecimal price) {
        Item item = getItem();
        item.price = price;
    }

    public void setItemDescription(String s) {
        getItem().description = s;
    }

    public void setItemBarcode(String s) {
        getItem().barcode = s;
    }

    public void updateItemImages(Collection<ItemImage> itemImages) {
        mItemImages.onNext(new ArrayList<>(itemImages));
    }

    @Override
    public ItemState clone() {
        ItemState clone = new ItemState();
        Item item = mItem.getValue();
        if (item != null) {
            clone.updateItem(item.clone());
        }
        ArrayList<ItemImage> itemImages = mItemImages.getValue();
        if (!itemImages.isEmpty()) {
            clone.updateItemImages(itemImages);
        }
        TreeSet<ItemTag> itemTags = mItemTags.getValue();
        if (!itemTags.isEmpty()) {
            clone.updateItemTags(itemTags);
        }
        return clone;
    }

    /**
     * Clone this state to be used as the basis for a new (duplicated) item.
     * Unlike {@link #clone()}, this clears every primary key / foreign key
     * (item id, image id/itemId, tag id/itemId) so that re-inserting produces
     * brand-new rows instead of colliding with the source rows (Room's
     * autoGenerate inserts a provided id as-is). The created/updated timestamps
     * are reset to now as well, so the new item is distinguishable from the
     * source in lists that key on createdDateTime. Field values such as
     * barcode, name, amount, price, description and expiredDateTime are copied
     * as-is for the user to review and edit before saving.
     */
    public ItemState cloneForDuplicate() {
        ItemState clone = new ItemState();
        Item item = mItem.getValue();
        if (item != null) {
            Item clonedItem = item.clone();
            Date now = new Date();
            clonedItem.id = null;
            clonedItem.createdDateTime = now;
            clonedItem.updatedDateTime = now;
            clone.updateItem(clonedItem);
        }
        ArrayList<ItemImage> itemImages = mItemImages.getValue();
        if (!itemImages.isEmpty()) {
            ArrayList<ItemImage> clonedImages = new ArrayList<>();
            for (ItemImage itemImage : itemImages) {
                ItemImage clonedImage = itemImage.clone();
                clonedImage.id = null;
                clonedImage.itemId = null;
                clonedImages.add(clonedImage);
            }
            clone.updateItemImages(clonedImages);
        }
        TreeSet<ItemTag> itemTags = mItemTags.getValue();
        if (!itemTags.isEmpty()) {
            TreeSet<ItemTag> clonedTags = new TreeSet<>();
            for (ItemTag itemTag : itemTags) {
                ItemTag clonedTag = itemTag.clone();
                clonedTag.id = null;
                clonedTag.itemId = null;
                clonedTags.add(clonedTag);
            }
            clone.updateItemTags(clonedTags);
        }
        return clone;
    }

    public void updateItemTags(Collection<ItemTag> itemTags) {
        mItemTags.onNext(new TreeSet<>(itemTags));
    }

    public Flowable<Item> getItemFlow() {
        return Flowable.fromObservable(mItem.getSubject(), BackpressureStrategy.BUFFER);
    }

    public void increaseItemAmount(int amt) {
        Item item = getItem();
        item.amount += amt;
        updateItem(item);
    }

    public void decreaseItemAmount(int amt) {
        Item item = getItem();
        item.amount -= amt;
        updateItem(item);
    }

    public void updateBarcode(String barcode) {
        Item item = getItem();
        item.barcode = barcode;
        updateItem(item);
    }

    public BigDecimal getItemPrice() {
        return getItem().price;
    }

    public String getItemBarcode() {
        return getItem().barcode;
    }

    public Date getItemCreatedDateTime() {
        return getItem().createdDateTime;
    }

    public Flowable<ArrayList<ItemImage>> getItemImagesFlow() {
        return Flowable.fromObservable(mItemImages.getSubject(), BackpressureStrategy.BUFFER);
    }

    public ArrayList<ItemImage> getItemImages() {
        return mItemImages.getValue();
    }

    public ItemImage getItemImage(int index) {
        List<ItemImage> itemImageList = mItemImages.getValue();
        if (index < 0 || index >= itemImageList.size()) return null;
        return itemImageList.get(index);
    }

    public Flowable<TreeSet<ItemTag>> getItemTagsFlow() {
        return Flowable.fromObservable(mItemTags.getSubject(), BackpressureStrategy.BUFFER);
    }

    public TreeSet<ItemTag> getItemTags() {
        return mItemTags.getValue();
    }

    public void addItemTag(ItemTag itemTag) {
        TreeSet<ItemTag> itemTags = mItemTags.getValue();
        itemTags.add(itemTag);
        mItemTags.onNext(itemTags);
    }
}
