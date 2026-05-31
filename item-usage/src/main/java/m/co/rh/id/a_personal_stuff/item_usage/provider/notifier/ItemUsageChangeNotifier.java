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
}
