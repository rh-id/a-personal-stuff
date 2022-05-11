package m.co.rh.id.a_personal_stuff.item_usage.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;

public class ItemUsageChangeNotifier {
    private PublishSubject<ItemUsageState> mAddedSubject;
    private PublishSubject<ItemUsageState> mUpdatedSubject;
    private PublishSubject<ItemUsageState> mDeletedSubject;
    private PublishSubject<ItemUsageImage> mImageAddedSubject;
    private PublishSubject<ItemUsageImage> mImageDeletedSubject;

    public ItemUsageChangeNotifier() {
        mAddedSubject = PublishSubject.create();
        mUpdatedSubject = PublishSubject.create();
        mDeletedSubject = PublishSubject.create();
        mImageAddedSubject = PublishSubject.create();
        mImageDeletedSubject = PublishSubject.create();
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
}
