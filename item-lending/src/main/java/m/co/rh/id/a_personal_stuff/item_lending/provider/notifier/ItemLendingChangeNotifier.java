package m.co.rh.id.a_personal_stuff.item_lending.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLendingImage;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;

public class ItemLendingChangeNotifier {
    private Subject<ItemLendingState> mAddedSubject;
    private Subject<ItemLendingState> mUpdatedSubject;
    private Subject<ItemLendingState> mDeletedSubject;
    private Subject<ItemLendingImage> mImageAddedSubject;
    private Subject<ItemLendingImage> mImageDeletedSubject;

    public ItemLendingChangeNotifier() {
        mAddedSubject = PublishSubject.<ItemLendingState>create().toSerialized();
        mUpdatedSubject = PublishSubject.<ItemLendingState>create().toSerialized();
        mDeletedSubject = PublishSubject.<ItemLendingState>create().toSerialized();
        mImageAddedSubject = PublishSubject.<ItemLendingImage>create().toSerialized();
        mImageDeletedSubject = PublishSubject.<ItemLendingImage>create().toSerialized();
    }

    public void itemLendingAdded(ItemLendingState itemLendingState) {
        mAddedSubject.onNext(itemLendingState);
    }

    public void itemLendingUpdated(ItemLendingState itemLendingState) {
        mUpdatedSubject.onNext(itemLendingState);
    }

    public void itemLendingDeleted(ItemLendingState itemLendingState) {
        mDeletedSubject.onNext(itemLendingState);
    }

    public void imageAdded(ItemLendingImage itemLendingImage) {
        mImageAddedSubject.onNext(itemLendingImage);
    }

    public void imageDeleted(ItemLendingImage itemLendingImage) {
        mImageDeletedSubject.onNext(itemLendingImage);
    }

    public Flowable<ItemLendingState> getAddedItemLendingFlow() {
        return Flowable.fromObservable(mAddedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemLendingState> getUpdatedItemLendingFlow() {
        return Flowable.fromObservable(mUpdatedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemLendingState> getDeletedItemLendingFlow() {
        return Flowable.fromObservable(mDeletedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemLendingImage> getDeletedItemLendingImageFlow() {
        return Flowable.fromObservable(mImageDeletedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemLendingImage> getAddedItemLendingImageFlow() {
        return Flowable.fromObservable(mImageAddedSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Emits the affected item id on any lending add/update/delete. Convenience
     * for consumers that only need to know "something about this item's
     * lendings changed" (e.g. to recompute a currently-lent badge).
     */
    public Flowable<Long> getAnyItemLendingChangeFlow() {
        return Flowable.merge(
                getAddedItemLendingFlow().map(s -> s.getItemId()),
                getUpdatedItemLendingFlow().map(s -> s.getItemId()),
                getDeletedItemLendingFlow().map(s -> s.getItemId()));
    }

    /**
     * Emits the affected lending id (ItemLendingImage.itemLendingId) on any
     * lending image add/delete. Convenience for consumers that need to know
     * "a lending's images changed" — e.g. to reload a list that displays
     * images. Unlike {@link #getAnyItemLendingChangeFlow()} this emits the
     * lending id (not the item id) because image events don't carry the item
     * id; the consumer can resolve it via the lending id if needed.
     */
    public Flowable<Long> getAnyItemLendingImageChangeFlow() {
        return Flowable.merge(
                getAddedItemLendingImageFlow().map(img -> img.itemLendingId),
                getDeletedItemLendingImageFlow().map(img -> img.itemLendingId));
    }
}
