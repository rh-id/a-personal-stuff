package m.co.rh.id.a_personal_stuff.item_purchase.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;

public class ItemPurchaseChangeNotifier {
    private Subject<ItemPurchaseState> mAddedSubject;
    private Subject<ItemPurchaseState> mUpdatedSubject;
    private Subject<ItemPurchaseState> mDeletedSubject;
    private Subject<ItemPurchaseImage> mImageAddedSubject;
    private Subject<ItemPurchaseImage> mImageDeletedSubject;

    public ItemPurchaseChangeNotifier() {
        mAddedSubject = PublishSubject.<ItemPurchaseState>create().toSerialized();
        mUpdatedSubject = PublishSubject.<ItemPurchaseState>create().toSerialized();
        mDeletedSubject = PublishSubject.<ItemPurchaseState>create().toSerialized();
        mImageAddedSubject = PublishSubject.<ItemPurchaseImage>create().toSerialized();
        mImageDeletedSubject = PublishSubject.<ItemPurchaseImage>create().toSerialized();
    }

    public void itemPurchaseAdded(ItemPurchaseState itemPurchaseState) {
        mAddedSubject.onNext(itemPurchaseState);
    }

    public void itemPurchaseUpdated(ItemPurchaseState itemPurchaseState) {
        mUpdatedSubject.onNext(itemPurchaseState);
    }

    public void itemPurchaseDeleted(ItemPurchaseState itemPurchaseState) {
        mDeletedSubject.onNext(itemPurchaseState);
    }

    public void imageAdded(ItemPurchaseImage itemPurchaseImage) {
        mImageAddedSubject.onNext(itemPurchaseImage);
    }

    public void imageDeleted(ItemPurchaseImage itemPurchaseImage) {
        mImageDeletedSubject.onNext(itemPurchaseImage);
    }

    public Flowable<ItemPurchaseState> getAddedItemPurchaseFlow() {
        return Flowable.fromObservable(mAddedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemPurchaseState> getUpdatedItemPurchaseFlow() {
        return Flowable.fromObservable(mUpdatedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemPurchaseState> getDeletedItemPurchaseFlow() {
        return Flowable.fromObservable(mDeletedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemPurchaseImage> getDeletedItemPurchaseImageFlow() {
        return Flowable.fromObservable(mImageDeletedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemPurchaseImage> getAddedItemPurchaseImageFlow() {
        return Flowable.fromObservable(mImageAddedSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Emits the affected item id on any purchase add/update/delete. Convenience
     * for consumers that only need to know "something about this item's
     * purchases changed" (e.g. to recompute a remaining-quantity badge).
     */
    public Flowable<Long> getAnyItemPurchaseChangeFlow() {
        return Flowable.merge(
                getAddedItemPurchaseFlow().map(s -> s.getItemId()),
                getUpdatedItemPurchaseFlow().map(s -> s.getItemId()),
                getDeletedItemPurchaseFlow().map(s -> s.getItemId()));
    }

    /**
     * Emits the affected purchase id (ItemPurchaseImage.itemPurchaseId) on any
     * purchase image add/delete. Convenience for consumers that need to know
     * "a purchase's images changed" — e.g. to reload a list that displays
     * images. Unlike {@link #getAnyItemPurchaseChangeFlow()} this emits the
     * purchase id (not the item id) because image events don't carry the item
     * id; the consumer can resolve it via the purchase id if needed.
     */
    public Flowable<Long> getAnyItemPurchaseImageChangeFlow() {
        return Flowable.merge(
                getAddedItemPurchaseImageFlow().map(img -> img.itemPurchaseId),
                getDeletedItemPurchaseImageFlow().map(img -> img.itemPurchaseId));
    }
}
