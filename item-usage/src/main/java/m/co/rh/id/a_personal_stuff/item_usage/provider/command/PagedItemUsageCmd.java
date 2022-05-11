package m.co.rh.id.a_personal_stuff.item_usage.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.aprovider.Provider;

public class PagedItemUsageCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private ItemUsageDao mItemUsageDao;
    private long mItemId;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<ItemUsageState>> mItemUsageStatesSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;

    public PagedItemUsageCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mItemUsageStatesSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
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
                mIsLoadingSubject.onNext(true);
                try {
                    mItemUsageStatesSubject.onNext(new ArrayList<>(
                            mItemUsageDao.searchItemUsageStateByItemId(mItemId, search))
                    );
                } catch (Throwable throwable) {
                    mItemUsageStatesSubject.onError(throwable);
                } finally {
                    mIsLoadingSubject.onNext(false);
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
            mIsLoadingSubject.onNext(true);
            try {
                mItemUsageStatesSubject.onNext(
                        loadItems());
            } catch (Throwable throwable) {
                mItemUsageStatesSubject.onError(throwable);
            } finally {
                mIsLoadingSubject.onNext(false);
            }
        });
    }

    private ArrayList<ItemUsageState> loadItems() {
        return new ArrayList<>(mItemUsageDao.findItemUsageStateByItemIdWithLimit(mItemId, mLimit));
    }

    public ArrayList<ItemUsageState> getAllItems() {
        return mItemUsageStatesSubject.getValue();
    }

    public Flowable<ArrayList<ItemUsageState>> getItemUsagesFlow() {
        return Flowable.fromObservable(mItemUsageStatesSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 30;
    }

    public void setItemId(long itemId) {
        mItemId = itemId;
    }
}
