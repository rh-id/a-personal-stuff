package m.co.rh.id.a_personal_stuff.item_reminder.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;

public class ItemReminderChangeNotifier {
    private PublishSubject<ItemReminder> mAddedSubject;
    private PublishSubject<ItemReminder> mDeletedSubject;

    public ItemReminderChangeNotifier() {
        mAddedSubject = PublishSubject.create();
        mDeletedSubject = PublishSubject.create();
    }

    public void added(ItemReminder itemReminder) {
        mAddedSubject.onNext(itemReminder);
    }

    public void deleted(ItemReminder itemReminder) {
        mDeletedSubject.onNext(itemReminder);
    }

    public Flowable<ItemReminder> getAddedFlow() {
        return Flowable.fromObservable(mAddedSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<ItemReminder> getDeletedFlow() {
        return Flowable.fromObservable(mDeletedSubject, BackpressureStrategy.BUFFER);
    }
}
