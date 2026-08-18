package m.co.rh.id.a_personal_stuff.item_checklist.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;

public class ItemChecklistState implements Serializable, Cloneable {
    private SerialBehaviorSubject<ItemChecklist> mItemChecklist;
    private SerialBehaviorSubject<ArrayList<ItemChecklistItem>> mItemChecklistItems;

    public ItemChecklistState() {
        mItemChecklist = new SerialBehaviorSubject<>(new ItemChecklist());
        mItemChecklistItems = new SerialBehaviorSubject<>(new ArrayList<>());
    }

    public void updateItemChecklist(ItemChecklist itemChecklist) {
        mItemChecklist.onNext(itemChecklist);
    }

    public ItemChecklist getItemChecklist() {
        return mItemChecklist.getValue();
    }

    public void updateItemChecklistItems(Collection<ItemChecklistItem> itemChecklistItems) {
        mItemChecklistItems.onNext(new ArrayList<>(itemChecklistItems));
    }

    public ArrayList<ItemChecklistItem> getItemChecklistItems() {
        return mItemChecklistItems.getValue();
    }

    @Override
    public ItemChecklistState clone() {
        ItemChecklistState clone = new ItemChecklistState();
        ItemChecklist itemChecklist = mItemChecklist.getValue();
        if (itemChecklist != null) {
            clone.updateItemChecklist(itemChecklist.clone());
        }
        ArrayList<ItemChecklistItem> itemChecklistItems = mItemChecklistItems.getValue();
        if (!itemChecklistItems.isEmpty()) {
            ArrayList<ItemChecklistItem> clonedItems = new ArrayList<>();
            for (ItemChecklistItem item : itemChecklistItems) {
                clonedItems.add(item.clone());
            }
            clone.updateItemChecklistItems(clonedItems);
        }
        return clone;
    }

    public void setTitle(String title) {
        getItemChecklist().title = title;
    }

    public String getDescription() {
        return getItemChecklist().description;
    }

    public void setDescription(String description) {
        getItemChecklist().description = description;
    }

    public Flowable<ItemChecklist> getItemChecklistFlow() {
        return Flowable.fromObservable(mItemChecklist.getSubject(), BackpressureStrategy.BUFFER);
    }

    public Flowable<ArrayList<ItemChecklistItem>> getItemChecklistItemsFlow() {
        return Flowable.fromObservable(mItemChecklistItems.getSubject(), BackpressureStrategy.BUFFER);
    }

    public Long getChecklistId() {
        return getItemChecklist().id;
    }

    public String getTitle() {
        return getItemChecklist().title;
    }

    public Date getCreatedDateTime() {
        return getItemChecklist().createdDateTime;
    }

    public Date getUpdatedDateTime() {
        return getItemChecklist().updatedDateTime;
    }

    public void setCreatedDateTime(Date date) {
        getItemChecklist().createdDateTime = date;
    }

    public void setUpdatedDateTime(Date date) {
        getItemChecklist().updatedDateTime = date;
    }
}
