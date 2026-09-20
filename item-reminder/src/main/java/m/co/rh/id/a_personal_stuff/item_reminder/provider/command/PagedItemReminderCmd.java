package m.co.rh.id.a_personal_stuff.item_reminder.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.aprovider.Provider;

public class PagedItemReminderCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private ItemReminderDao mItemReminderDao;
    private ItemDao mItemDao;
    /** Nullable — null loads reminders of every item (global mode). */
    private Long mItemId;
    /**
     * itemId → item name, refreshed alongside every global-mode load on the
     * executor so getItemName never touches the DB from the UI thread. Empty
     * in per-item mode (the name would be redundant there).
     */
    private volatile Map<Long, String> mItemNames;
    private int mLimit;
    private final BehaviorSubject<ArrayList<ItemReminder>> mItemRemindersSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final Subject<ArrayList<ItemReminder>> mItemRemindersEmitter;
    private final Subject<Boolean> mIsLoadingEmitter;

    public PagedItemReminderCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mItemDao = provider.get(ItemDao.class);
        mItemNames = new HashMap<>();
        mItemRemindersSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mItemRemindersEmitter = mItemRemindersSubject.toSerialized();
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        mIsLoadingEmitter = mIsLoadingSubject.toSerialized();
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
            mIsLoadingEmitter.onNext(true);
            try {
                mItemRemindersEmitter.onNext(
                        loadItems());
            } catch (Throwable throwable) {
                mItemRemindersEmitter.onNext(mItemRemindersSubject.getValue());
            } finally {
                mIsLoadingEmitter.onNext(false);
            }
        });
    }

    private ArrayList<ItemReminder> loadItems() {
        if (mItemId == null) {
            // fetch the names before the reminders so the map is ready when
            // the emitted list triggers row binding
            Map<Long, String> itemNames = new HashMap<>();
            for (Item item : mItemDao.findAllItems()) {
                itemNames.put(item.id, item.name);
            }
            mItemNames = itemNames;
            return new ArrayList<>(mItemReminderDao.findAllItemRemindersWithLimit(mLimit));
        }
        mItemNames = new HashMap<>();
        return new ArrayList<>(mItemReminderDao.findItemReminderByItemIdWithLimit(mItemId, mLimit));
    }

    /**
     * Name of the item a reminder belongs to, or null when unknown (per-item
     * mode, an orphaned reminder, or before the first global load). Reads the
     * pre-fetched map only — never queries the DB.
     */
    public String getItemName(Long itemId) {
        if (itemId == null) {
            return null;
        }
        return mItemNames.get(itemId);
    }

    public ArrayList<ItemReminder> getAllItems() {
        return mItemRemindersSubject.getValue();
    }

    public Flowable<ArrayList<ItemReminder>> getItemRemindersFlow() {
        return Flowable.fromObservable(mItemRemindersEmitter, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingEmitter, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 100;
    }

    public void setItemId(Long itemId) {
        mItemId = itemId;
    }
}
