package m.co.rh.id.a_personal_stuff.item_reminder.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.aprovider.Provider;

public class PagedItemReminderCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private ItemReminderDao mItemReminderDao;
    private long mItemId;
    private int mLimit;
    private final BehaviorSubject<ArrayList<ItemReminder>> mItemRemindersSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;

    public PagedItemReminderCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mItemRemindersSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        resetPage();
    }

    public void loadNextPage() {
        // no pagination for search
        if (getAllItems().size() < mLimit) {
            return;
        }
        mLimit += mLimit;
        load();
    }

    public void refresh() {
        load();
    }

    private void load() {
        mExecutorService.execute(() -> {
            mIsLoadingSubject.onNext(true);
            try {
                mItemRemindersSubject.onNext(
                        loadItems());
            } catch (Throwable throwable) {
                mItemRemindersSubject.onError(throwable);
            } finally {
                mIsLoadingSubject.onNext(false);
            }
        });
    }

    private ArrayList<ItemReminder> loadItems() {
        return new ArrayList<>(mItemReminderDao.findItemReminderByItemIdWithLimit(mItemId, mLimit));
    }

    public ArrayList<ItemReminder> getAllItems() {
        return mItemRemindersSubject.getValue();
    }

    public Flowable<ArrayList<ItemReminder>> getItemRemindersFlow() {
        return Flowable.fromObservable(mItemRemindersSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 100;
    }

    public void setItemId(long itemId) {
        mItemId = itemId;
    }
}
