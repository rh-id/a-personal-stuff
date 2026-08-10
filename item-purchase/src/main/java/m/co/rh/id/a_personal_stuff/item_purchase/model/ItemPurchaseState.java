package m.co.rh.id.a_personal_stuff.item_purchase.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;

public class ItemPurchaseState implements Serializable, Cloneable {
    private SerialBehaviorSubject<ItemPurchase> mItemPurchase;
    private SerialBehaviorSubject<ArrayList<ItemPurchaseImage>> mItemPurchaseImages;

    public ItemPurchaseState() {
        mItemPurchase = new SerialBehaviorSubject<>(new ItemPurchase());
        mItemPurchaseImages = new SerialBehaviorSubject<>(new ArrayList<>());
    }

    public void updateItemPurchase(ItemPurchase itemPurchase) {
        mItemPurchase.onNext(itemPurchase);
    }

    public ItemPurchase getItemPurchase() {
        return mItemPurchase.getValue();
    }

    public void updateItemPurchaseImages(Collection<ItemPurchaseImage> itemPurchaseImages) {
        mItemPurchaseImages.onNext(new ArrayList<>(itemPurchaseImages));
    }

    public ArrayList<ItemPurchaseImage> getItemPurchaseImages() {
        return mItemPurchaseImages.getValue();
    }

    @Override
    public ItemPurchaseState clone() {
        ItemPurchaseState clone = new ItemPurchaseState();
        ItemPurchase itemPurchase = mItemPurchase.getValue();
        if (itemPurchase != null) {
            clone.updateItemPurchase(itemPurchase.clone());
        }
        ArrayList<ItemPurchaseImage> itemPurchaseImages = mItemPurchaseImages.getValue();
        if (!itemPurchaseImages.isEmpty()) {
            clone.updateItemPurchaseImages(itemPurchaseImages);
        }
        return clone;
    }

    public void setAmount(int amount) {
        getItemPurchase().amount = amount;
    }

    public Flowable<ItemPurchase> getItemPurchaseFlow() {
        return Flowable.fromObservable(mItemPurchase.getSubject(), BackpressureStrategy.BUFFER);
    }

    public void setDescription(String description) {
        getItemPurchase().description = description;
    }

    public void increaseAmount(int amt) {
        ItemPurchase itemPurchase = getItemPurchase();
        itemPurchase.amount += amt;
        updateItemPurchase(itemPurchase);
    }

    public void decreaseAmount(int amt) {
        ItemPurchase itemPurchase = getItemPurchase();
        itemPurchase.amount -= amt;
        updateItemPurchase(itemPurchase);
    }

    public void setItemId(long itemId) {
        getItemPurchase().itemId = itemId;
    }

    public Flowable<ArrayList<ItemPurchaseImage>> getItemPurchaseImagesFlow() {
        return Flowable.fromObservable(mItemPurchaseImages.getSubject(), BackpressureStrategy.BUFFER);
    }

    public Long getItemPurchaseId() {
        return getItemPurchase().id;
    }

    public Date getItemPurchaseCreatedDateTime() {
        return getItemPurchase().createdDateTime;
    }

    public int getItemPurchaseAmount() {
        return getItemPurchase().amount;
    }

    public String getItemPurchaseDescription() {
        return getItemPurchase().description;
    }

    public Long getItemId() {
        return getItemPurchase().itemId;
    }

    public void setItemPurchaseCost(BigDecimal cost) {
        getItemPurchase().cost = cost;
    }

    public BigDecimal getItemPurchaseCost() {
        return getItemPurchase().cost;
    }

    public Date getPurchaseDateTime() {
        return getItemPurchase().purchaseDateTime;
    }

    public void updatePurchaseDateTime(Date dateTime) {
        ItemPurchase itemPurchase = getItemPurchase();
        itemPurchase.purchaseDateTime = dateTime;
        mItemPurchase.onNext(itemPurchase);
    }
}
