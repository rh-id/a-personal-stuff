package m.co.rh.id.a_personal_stuff.item_lending.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLending;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLendingImage;

public class ItemLendingState implements Serializable, Cloneable {
    private SerialBehaviorSubject<ItemLending> mItemLending;
    private SerialBehaviorSubject<ArrayList<ItemLendingImage>> mItemLendingImages;

    public ItemLendingState() {
        mItemLending = new SerialBehaviorSubject<>(new ItemLending());
        mItemLendingImages = new SerialBehaviorSubject<>(new ArrayList<>());
    }

    public void updateItemLending(ItemLending itemLending) {
        mItemLending.onNext(itemLending);
    }

    public ItemLending getItemLending() {
        return mItemLending.getValue();
    }

    public void updateItemLendingImages(Collection<ItemLendingImage> itemLendingImages) {
        mItemLendingImages.onNext(new ArrayList<>(itemLendingImages));
    }

    public ArrayList<ItemLendingImage> getItemLendingImages() {
        return mItemLendingImages.getValue();
    }

    @Override
    public ItemLendingState clone() {
        ItemLendingState clone = new ItemLendingState();
        ItemLending itemLending = mItemLending.getValue();
        if (itemLending != null) {
            clone.updateItemLending(itemLending.clone());
        }
        ArrayList<ItemLendingImage> itemLendingImages = mItemLendingImages.getValue();
        if (!itemLendingImages.isEmpty()) {
            clone.updateItemLendingImages(itemLendingImages);
        }
        return clone;
    }

    public void setItemId(long itemId) {
        getItemLending().itemId = itemId;
    }

    public Flowable<ItemLending> getItemLendingFlow() {
        return Flowable.fromObservable(mItemLending.getSubject(), BackpressureStrategy.BUFFER);
    }

    public void setBorrowerName(String borrowerName) {
        getItemLending().borrowerName = borrowerName;
    }

    public void setNote(String note) {
        getItemLending().note = note;
    }

    public Flowable<ArrayList<ItemLendingImage>> getItemLendingImagesFlow() {
        return Flowable.fromObservable(mItemLendingImages.getSubject(), BackpressureStrategy.BUFFER);
    }

    public Long getItemLendingId() {
        return getItemLending().id;
    }

    public String getItemLendingBorrowerName() {
        return getItemLending().borrowerName;
    }

    public Long getItemId() {
        return getItemLending().itemId;
    }

    public Date getLendDateTime() {
        return getItemLending().lendDateTime;
    }

    public Date getDueDateTime() {
        return getItemLending().dueDateTime;
    }

    public Date getReturnedDateTime() {
        return getItemLending().returnedDateTime;
    }

    public void updateLendDateTime(Date dateTime) {
        ItemLending itemLending = getItemLending();
        itemLending.lendDateTime = dateTime;
        mItemLending.onNext(itemLending);
    }

    public void updateDueDateTime(Date dateTime) {
        ItemLending itemLending = getItemLending();
        itemLending.dueDateTime = dateTime;
        mItemLending.onNext(itemLending);
    }

    public void updateReturnedDateTime(Date dateTime) {
        ItemLending itemLending = getItemLending();
        itemLending.returnedDateTime = dateTime;
        mItemLending.onNext(itemLending);
    }
}
