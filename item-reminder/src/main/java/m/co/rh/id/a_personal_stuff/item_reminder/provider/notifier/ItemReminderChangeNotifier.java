package m.co.rh.id.a_personal_stuff.item_reminder.provider.notifier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;

public class ItemReminderChangeNotifier {
    private Subject<ItemReminder> mAddedSubject;
    private Subject<ItemReminder> mDeletedSubject;

    public ItemReminderChangeNotifier() {
        mAddedSubject = PublishSubject.<ItemReminder>create().toSerialized();
        mDeletedSubject = PublishSubject.<ItemReminder>create().toSerialized();
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
