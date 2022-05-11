package m.co.rh.id.a_personal_stuff.item_usage.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;

public class ItemUsageState implements Serializable, Cloneable {
    private SerialBehaviorSubject<ItemUsage> mItemUsage;
    private SerialBehaviorSubject<ArrayList<ItemUsageImage>> mItemUsageImages;

    public ItemUsageState() {
        mItemUsage = new SerialBehaviorSubject<>(new ItemUsage());
        mItemUsageImages = new SerialBehaviorSubject<>(new ArrayList<>());
    }

    public void updateItemUsage(ItemUsage itemUsage) {
        mItemUsage.onNext(itemUsage);
    }

    public ItemUsage getItemUsage() {
        return mItemUsage.getValue();
    }

    public void updateItemUsageImages(Collection<ItemUsageImage> itemUsageImages) {
        mItemUsageImages.onNext(new ArrayList<>(itemUsageImages));
    }

    public ArrayList<ItemUsageImage> getItemUsageImages() {
        return mItemUsageImages.getValue();
    }

    @Override
    public ItemUsageState clone() {
        ItemUsageState clone = new ItemUsageState();
        ItemUsage itemUsage = mItemUsage.getValue();
        if (itemUsage != null) {
            clone.updateItemUsage(itemUsage.clone());
        }
        ArrayList<ItemUsageImage> itemUsageImages = mItemUsageImages.getValue();
        if (!itemUsageImages.isEmpty()) {
            clone.updateItemUsageImages(itemUsageImages);
        }
        return clone;
    }

    public void setAmount(int amount) {
        getItemUsage().amount = amount;
    }

    public Flowable<ItemUsage> getItemUsageFlow() {
        return Flowable.fromObservable(mItemUsage.getSubject(), BackpressureStrategy.BUFFER);
    }

    public void setDescription(String description) {
        getItemUsage().description = description;
    }

    public void increaseAmount(int amt) {
        ItemUsage itemUsage = getItemUsage();
        itemUsage.amount += amt;
        updateItemUsage(itemUsage);
    }

    public void decreaseAmount(int amt) {
        ItemUsage itemUsage = getItemUsage();
        itemUsage.amount -= amt;
        updateItemUsage(itemUsage);
    }

    public void setItemId(long itemId) {
        getItemUsage().itemId = itemId;
    }

    public Flowable<ArrayList<ItemUsageImage>> getItemUsageImagesFlow() {
        return Flowable.fromObservable(mItemUsageImages.getSubject(), BackpressureStrategy.BUFFER);
    }

    public Long getItemUsageId() {
        return getItemUsage().itemId;
    }

    public Date getItemUsageCreatedDateTime() {
        return getItemUsage().createdDateTime;
    }

    public int getItemUsageAmount() {
        return getItemUsage().amount;
    }

    public String getItemUsageDescription() {
        return getItemUsage().description;
    }
}
