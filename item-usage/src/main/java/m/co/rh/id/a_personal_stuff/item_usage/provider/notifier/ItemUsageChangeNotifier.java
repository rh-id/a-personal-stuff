package m.co.rh.id.a_personal_stuff.item_usage.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;

public class ItemUsageChangeNotifier {
    private Subject<ItemUsageState> mAddedSubject;
    private Subject<ItemUsageState> mUpdatedSubject;
    private Subject<ItemUsageState> mDeletedSubject;
    private Subject<ItemUsageImage> mImageAddedSubject;
    private Subject<ItemUsageImage> mImageDeletedSubject;

    public ItemUsageChangeNotifier() {
        mAddedSubject = PublishSubject.<ItemUsageState>create().toSerialized();
        mUpdatedSubject = PublishSubject.<ItemUsageState>create().toSerialized();
        mDeletedSubject = PublishSubject.<ItemUsageState>create().toSerialized();
        mImageAddedSubject = PublishSubject.<ItemUsageImage>create().toSerialized();
        mImageDeletedSubject = PublishSubject.<ItemUsageImage>create().toSerialized();
    }

    public void itemUsageAdded(ItemUsageState itemUsageState) {
        mAddedSubject.onNext(itemUsageState);
    }

    public void itemUsageUpdated(ItemUsageState itemUsageState) {
        mUpdatedSubject.onNext(itemUsageState);
    }

    public void itemUsageDeleted(ItemUsageState itemUsageState) {
        mDeletedSubject.onNext(itemUsageState);
    }

    public void imageAdded(ItemUsageImage itemUsageImage) {
        mImageAddedSubject.onNext(itemUsageImage);
    }

    public void imageDeleted(ItemUsageImage itemUsageImage) {
        mImageDeletedSubject.onNext(itemUsageImage);
    }

    public Flowable<ItemUsageState> getAddedItemUsageFlow() {
        return Flowable.fromObservable(mAddedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemUsageState> getUpdatedItemUsageFlow() {
        return Flowable.fromObservable(mUpdatedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemUsageState> getDeletedItemUsageFlow() {
        return Flowable.fromObservable(mDeletedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemUsageImage> getDeletedItemUsageImageFlow() {
        return Flowable.fromObservable(mImageDeletedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemUsageImage> getAddedItemUsageImageFlow() {
        return Flowable.fromObservable(mImageAddedSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Emits the affected item id on any usage add/update/delete. Convenience
     * for consumers that only need to know "something about this item's
     * usages changed" (e.g. to recompute a remaining-quantity badge).
     */
    public Flowable<Long> getAnyItemUsageChangeFlow() {
        return Flowable.merge(
                getAddedItemUsageFlow().map(s -> s.getItemId()),
                getUpdatedItemUsageFlow().map(s -> s.getItemId()),
                getDeletedItemUsageFlow().map(s -> s.getItemId()));
    }

    /**
     * Emits the affected usage id (ItemUsageImage.itemUsageId) on any usage
     * image add/delete. Convenience for consumers that need to know "a usage's
     * images changed" — e.g. to reload a list that displays images. Unlike
     * {@link #getAnyItemUsageChangeFlow()} this emits the usage id (not the
     * item id) because image events don't carry the item id; the consumer can
     * resolve it via the usage id if needed.
     */
    public Flowable<Long> getAnyItemUsageImageChangeFlow() {
        return Flowable.merge(
                getAddedItemUsageImageFlow().map(img -> img.itemUsageId),
                getDeletedItemUsageImageFlow().map(img -> img.itemUsageId));
    }
}
