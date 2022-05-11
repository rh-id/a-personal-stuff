package m.co.rh.id.a_personal_stuff.item_maintenance.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;

public class ItemMaintenanceState implements Serializable, Cloneable {
    private SerialBehaviorSubject<ItemMaintenance> mItemMaintenance;
    private SerialBehaviorSubject<ArrayList<ItemMaintenanceImage>> mItemMaintenanceImages;

    public ItemMaintenanceState() {
        mItemMaintenance = new SerialBehaviorSubject<>(new ItemMaintenance());
        mItemMaintenanceImages = new SerialBehaviorSubject<>(new ArrayList<>());
    }

    public void updateItemMaintenance(ItemMaintenance itemMaintenance) {
        mItemMaintenance.onNext(itemMaintenance);
    }

    public ItemMaintenance getItemMaintenance() {
        return mItemMaintenance.getValue();
    }

    public void updateItemMaintenanceImages(Collection<ItemMaintenanceImage> itemMaintenanceImages) {
        mItemMaintenanceImages.onNext(new ArrayList<>(itemMaintenanceImages));
    }

    public ArrayList<ItemMaintenanceImage> getItemMaintenanceImages() {
        return mItemMaintenanceImages.getValue();
    }

    @Override
    public ItemMaintenanceState clone() {
        ItemMaintenanceState clone = new ItemMaintenanceState();
        ItemMaintenance itemMaintenance = mItemMaintenance.getValue();
        if (itemMaintenance != null) {
            clone.updateItemMaintenance(itemMaintenance.clone());
        }
        ArrayList<ItemMaintenanceImage> itemMaintenanceImages = mItemMaintenanceImages.getValue();
        if (!itemMaintenanceImages.isEmpty()) {
            clone.updateItemMaintenanceImages(itemMaintenanceImages);
        }
        return clone;
    }

    public Flowable<ItemMaintenance> getItemMaintenanceFlow() {
        return Flowable.fromObservable(mItemMaintenance.getSubject(), BackpressureStrategy.BUFFER);
    }

    public Flowable<ArrayList<ItemMaintenanceImage>> getItemMaintenanceImagesFlow() {
        return Flowable.fromObservable(mItemMaintenanceImages.getSubject(), BackpressureStrategy.BUFFER);
    }

    public void setItemMaintenanceCost(BigDecimal cost) {
        getItemMaintenance().cost = cost;
    }

    public Date getItemMaintenanceDateTime() {
        return getItemMaintenance().maintenanceDateTime;
    }

    public void updateItemMaintenanceDateTime(Date dateTime) {
        ItemMaintenance itemMaintenance = getItemMaintenance();
        itemMaintenance.maintenanceDateTime = dateTime;
        mItemMaintenance.onNext(itemMaintenance);
    }

    public void setItemMaintenanceDescription(String description) {
        getItemMaintenance().description = description;
    }

    public void setItemMaintenanceItemId(long itemId) {
        getItemMaintenance().itemId = itemId;
    }

    public Long getItemMaintenanceId() {
        return getItemMaintenance().id;
    }

    public String getItemMaintenanceDescription() {
        return getItemMaintenance().description;
    }

    public BigDecimal getItemMaintenanceCost() {
        return getItemMaintenance().cost;
    }

    public Date getItemMaintenanceCreatedDateTime() {
        return getItemMaintenance().createdDateTime;
    }
}
