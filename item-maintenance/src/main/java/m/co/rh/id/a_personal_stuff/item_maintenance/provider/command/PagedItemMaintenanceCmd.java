package m.co.rh.id.a_personal_stuff.item_maintenance.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.aprovider.Provider;

public class PagedItemMaintenanceCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private ItemMaintenanceDao mItemMaintenanceDao;
    private long mItemId;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<ItemMaintenanceState>> mItemMaintenanceStatesSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;

    public PagedItemMaintenanceCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mItemMaintenanceStatesSubject = BehaviorSubject.createDefault(new ArrayList<>());
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
                    mItemMaintenanceStatesSubject.onNext(new ArrayList<>(
                            mItemMaintenanceDao.searchItemMaintenanceStateByItemId(mItemId, search))
                    );
                } catch (Throwable throwable) {
                    mItemMaintenanceStatesSubject.onError(throwable);
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
                mItemMaintenanceStatesSubject.onNext(
                        loadItems());
            } catch (Throwable throwable) {
                mItemMaintenanceStatesSubject.onError(throwable);
            } finally {
                mIsLoadingSubject.onNext(false);
            }
        });
    }

    private ArrayList<ItemMaintenanceState> loadItems() {
        return new ArrayList<>(mItemMaintenanceDao.findItemMaintenanceStateByItemIdWithLimit(mItemId, mLimit));
    }

    public ArrayList<ItemMaintenanceState> getAllItems() {
        return mItemMaintenanceStatesSubject.getValue();
    }

    public Flowable<ArrayList<ItemMaintenanceState>> getItemMaintenanceStatesFlow() {
        return Flowable.fromObservable(mItemMaintenanceStatesSubject, BackpressureStrategy.BUFFER);
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
