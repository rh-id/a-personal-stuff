package m.co.rh.id.a_personal_stuff.base.provider.notifier;

import java.util.List;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;

public class ItemChangeNotifier {
    private PublishSubject<ItemState> mAddedSubject;
    private PublishSubject<ItemState> mUpdatedSubject;
    private PublishSubject<ItemState> mDeletedSubject;
    private PublishSubject<List<ItemImage>> mDeletedItemImagesSubject;
    private PublishSubject<ItemImage> mAddedItemImageSubject;
    private PublishSubject<ItemTag> mDeletedItemTagSubject;
    private PublishSubject<ItemTag> mAddedItemTagSubject;

    public ItemChangeNotifier() {
        mAddedSubject = PublishSubject.create();
        mUpdatedSubject = PublishSubject.create();
        mDeletedSubject = PublishSubject.create();
        mDeletedItemImagesSubject = PublishSubject.create();
        mAddedItemImageSubject = PublishSubject.create();
        mDeletedItemTagSubject = PublishSubject.create();
        mAddedItemTagSubject = PublishSubject.create();
    }

    public void itemAdded(ItemState itemState) {
        mAddedSubject.onNext(itemState);
    }

    public void itemUpdated(ItemState itemState) {
        mUpdatedSubject.onNext(itemState);
    }

    public void itemDeleted(ItemState itemState) {
        mDeletedSubject.onNext(itemState);
    }

    public Flowable<ItemState> getAddedItemFlow() {
        return Flowable.fromObservable(mAddedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemState> getUpdatedItemFlow() {
        return Flowable.fromObservable(mUpdatedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemState> getDeletedItemFlow() {
        return Flowable.fromObservable(mDeletedSubject, BackpressureStrategy.BUFFER);
    }

    public void itemImageDeleted(List<ItemImage> itemImages) {
        mDeletedItemImagesSubject.onNext(itemImages);
    }

    public Flowable<List<ItemImage>> getDeletedItemImagesFlow() {
        return Flowable.fromObservable(mDeletedItemImagesSubject, BackpressureStrategy.BUFFER);
    }

    public void itemImageAdded(ItemImage itemImage) {
        mAddedItemImageSubject.onNext(itemImage);
    }

    public Flowable<ItemImage> getAddedItemImageFlow() {
        return Flowable.fromObservable(mAddedItemImageSubject, BackpressureStrategy.BUFFER);
    }

    public void itemTagDeleted(ItemTag itemTag) {
        mDeletedItemTagSubject.onNext(itemTag);
    }

    public Flowable<ItemTag> getDeletedItemTagFlow() {
        return Flowable.fromObservable(mDeletedItemTagSubject, BackpressureStrategy.BUFFER);
    }

    public void itemTagAdded(ItemTag itemTag) {
        mAddedItemTagSubject.onNext(itemTag);
    }

    public Flowable<ItemTag> getAddedItemTagFlow() {
        return Flowable.fromObservable(mAddedItemTagSubject, BackpressureStrategy.BUFFER);
    }
}
