package m.co.rh.id.a_personal_stuff.item_lending.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_lending.dao.ItemLendingDao;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.aprovider.Provider;

public class PagedItemLendingCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private ItemLendingDao mItemLendingDao;
    private long mItemId;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<ItemLendingState>> mItemLendingStatesSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final Subject<ArrayList<ItemLendingState>> mItemLendingStatesEmitter;
    private final Subject<Boolean> mIsLoadingEmitter;

    public PagedItemLendingCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemLendingDao = provider.get(ItemLendingDao.class);
        mItemLendingStatesSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mItemLendingStatesEmitter = mItemLendingStatesSubject.toSerialized();
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        mIsLoadingEmitter = mIsLoadingSubject.toSerialized();
        resetPage();
    }

    private boolean isSearching() {
        return mSearch != null && !mSearch.isEmpty();
    }

    public void search(String search) {
        mSearch = search;
        mExecutorService.execute(() -> {
            if (!isSearching()) {
                load();
            } else {
                mIsLoadingEmitter.onNext(true);
                try {
                    mItemLendingStatesEmitter.onNext(new ArrayList<>(
                            mItemLendingDao.searchItemLendingStateByItemId(mItemId, search))
                    );
                } catch (Throwable throwable) {
                    mItemLendingStatesEmitter.onNext(new ArrayList<>());
                } finally {
                    mIsLoadingEmitter.onNext(false);
                }
            }
        });
    }

    public void loadNextPage() {
        // no pagination for search
        if (isSearching()) return;
        if (getAllItems().size() < mLimit) {
            return;
        }
        mLimit += mLimit;
        load();
    }

    public void refresh() {
        if (isSearching()) {
            doSearch();
        } else {
            load();
        }
    }

    private void doSearch() {
        search(mSearch);
    }

    private void load() {
        mExecutorService.execute(() -> {
            mIsLoadingEmitter.onNext(true);
            try {
                mItemLendingStatesEmitter.onNext(
                        loadItems());
            } catch (Throwable throwable) {
                mItemLendingStatesEmitter.onNext(mItemLendingStatesSubject.getValue());
            } finally {
                mIsLoadingEmitter.onNext(false);
            }
        });
    }

    private ArrayList<ItemLendingState> loadItems() {
        return new ArrayList<>(mItemLendingDao.findItemLendingStateByItemIdWithLimit(mItemId, mLimit));
    }

    public ArrayList<ItemLendingState> getAllItems() {
        return mItemLendingStatesSubject.getValue();
    }

    public Flowable<ArrayList<ItemLendingState>> getItemLendingsFlow() {
        return Flowable.fromObservable(mItemLendingStatesEmitter, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingEmitter, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 100;
    }

    public void setItemId(long itemId) {
        mItemId = itemId;
    }
}
