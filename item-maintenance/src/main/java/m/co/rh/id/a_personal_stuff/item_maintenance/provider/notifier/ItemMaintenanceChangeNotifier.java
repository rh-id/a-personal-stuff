package m.co.rh.id.a_personal_stuff.item_maintenance.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;

public class ItemMaintenanceChangeNotifier {
    private PublishSubject<ItemMaintenanceState> mAddedSubject;
    private PublishSubject<ItemMaintenanceState> mUpdatedSubject;
    private PublishSubject<ItemMaintenanceState> mDeletedSubject;
    private PublishSubject<ItemMaintenanceImage> mAddedImageSubject;
    private PublishSubject<ItemMaintenanceImage> mDeletedImageSubject;

    public ItemMaintenanceChangeNotifier() {
        mAddedSubject = PublishSubject.create();
        mUpdatedSubject = PublishSubject.create();
        mDeletedSubject = PublishSubject.create();
        mAddedImageSubject = PublishSubject.create();
        mDeletedImageSubject = PublishSubject.create();
    }

    public void itemMaintenanceAdded(ItemMaintenanceState itemMaintenanceState) {
        mAddedSubject.onNext(itemMaintenanceState);
    }

    public void itemMaintenanceUpdated(ItemMaintenanceState itemMaintenanceState) {
        mUpdatedSubject.onNext(itemMaintenanceState);
    }

    public void itemMaintenanceDeleted(ItemMaintenanceState itemMaintenanceState) {
        mDeletedSubject.onNext(itemMaintenanceState);
    }

    public Flowable<ItemMaintenanceState> getAddedItemMaintenanceFlow() {
        return Flowable.fromObservable(mAddedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemMaintenanceState> getUpdatedItemMaintenanceFlow() {
        return Flowable.fromObservable(mUpdatedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemMaintenanceState> getDeletedItemMaintenanceFlow() {
        return Flowable.fromObservable(mDeletedSubject, BackpressureStrategy.BUFFER);
    }

    public void imageAdded(ItemMaintenanceImage itemMaintenanceImage) {
        mAddedImageSubject.onNext(itemMaintenanceImage);
    }

    public Flowable<ItemMaintenanceImage> getAddedImageFlow() {
        return Flowable.fromObservable(mAddedImageSubject, BackpressureStrategy.BUFFER);
    }

    public void imageDeleted(ItemMaintenanceImage itemMaintenanceImage) {
        mDeletedImageSubject.onNext(itemMaintenanceImage);
    }

    public Flowable<ItemMaintenanceImage> getDeletedImageFlow() {
        return Flowable.fromObservable(mDeletedImageSubject, BackpressureStrategy.BUFFER);
    }
}
