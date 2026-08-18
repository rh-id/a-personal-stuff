package m.co.rh.id.a_personal_stuff.item_checklist.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;

public class ItemChecklistChangeNotifier {
    private Subject<ItemChecklistState> mChecklistAddedSubject;
    private Subject<ItemChecklistState> mChecklistUpdatedSubject;
    private Subject<ItemChecklistState> mChecklistDeletedSubject;
    private Subject<ItemChecklistItem> mChecklistItemAddedSubject;
    private Subject<ItemChecklistItem> mChecklistItemUpdatedSubject;
    private Subject<ItemChecklistItem> mChecklistItemDeletedSubject;

    public ItemChecklistChangeNotifier() {
        mChecklistAddedSubject = PublishSubject.<ItemChecklistState>create().toSerialized();
        mChecklistUpdatedSubject = PublishSubject.<ItemChecklistState>create().toSerialized();
        mChecklistDeletedSubject = PublishSubject.<ItemChecklistState>create().toSerialized();
        mChecklistItemAddedSubject = PublishSubject.<ItemChecklistItem>create().toSerialized();
        mChecklistItemUpdatedSubject = PublishSubject.<ItemChecklistItem>create().toSerialized();
        mChecklistItemDeletedSubject = PublishSubject.<ItemChecklistItem>create().toSerialized();
    }

    public void checklistAdded(ItemChecklistState itemChecklistState) {
        mChecklistAddedSubject.onNext(itemChecklistState);
    }

    public void checklistUpdated(ItemChecklistState itemChecklistState) {
        mChecklistUpdatedSubject.onNext(itemChecklistState);
    }

    public void checklistDeleted(ItemChecklistState itemChecklistState) {
        mChecklistDeletedSubject.onNext(itemChecklistState);
    }

    public void checklistItemAdded(ItemChecklistItem itemChecklistItem, ItemChecklistState itemChecklistState) {
        mChecklistItemAddedSubject.onNext(itemChecklistItem);
    }

    public void checklistItemUpdated(ItemChecklistItem itemChecklistItem, ItemChecklistState itemChecklistState) {
        mChecklistItemUpdatedSubject.onNext(itemChecklistItem);
    }

    public void checklistItemDeleted(ItemChecklistItem itemChecklistItem, ItemChecklistState itemChecklistState) {
        mChecklistItemDeletedSubject.onNext(itemChecklistItem);
    }

    public Flowable<ItemChecklistState> getChecklistAddedFlow() {
        return Flowable.fromObservable(mChecklistAddedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemChecklistState> getChecklistUpdatedFlow() {
        return Flowable.fromObservable(mChecklistUpdatedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemChecklistState> getChecklistDeletedFlow() {
        return Flowable.fromObservable(mChecklistDeletedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemChecklistItem> getChecklistItemAddedFlow() {
        return Flowable.fromObservable(mChecklistItemAddedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemChecklistItem> getChecklistItemUpdatedFlow() {
        return Flowable.fromObservable(mChecklistItemUpdatedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemChecklistItem> getChecklistItemDeletedFlow() {
        return Flowable.fromObservable(mChecklistItemDeletedSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Emits the affected checklist on any checklist add/update/delete.
     * Convenience for consumers that only need to know "something about this checklist changed".
     */
    public Flowable<ItemChecklistState> getAnyItemChecklistChangeFlow() {
        return Flowable.merge(
                getChecklistAddedFlow(),
                getChecklistUpdatedFlow(),
                getChecklistDeletedFlow());
    }

    /**
     * Emits the affected checklist id on any checklist item add/update/delete.
     * Convenience for consumers that only need to know "something about this checklist's items changed".
     */
    public Flowable<Long> getAnyItemChecklistItemChangeFlow() {
        return Flowable.merge(
                getChecklistItemAddedFlow().map(item -> item.itemChecklistId),
                getChecklistItemUpdatedFlow().map(item -> item.itemChecklistId),
                getChecklistItemDeletedFlow().map(item -> item.itemChecklistId));
    }
}
