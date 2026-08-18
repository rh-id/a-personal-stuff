package m.co.rh.id.a_personal_stuff.item_checklist.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.aprovider.Provider;

public class PagedItemChecklistCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private ItemChecklistDao mItemChecklistDao;
    private int mLimit;
    private String mSearch;
    private ItemChecklistDao.QueryOrderBy mQueryOrderBy;
    private final BehaviorSubject<ArrayList<ItemChecklistState>> mItemChecklistStatesSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final Subject<ArrayList<ItemChecklistState>> mItemChecklistStatesEmitter;
    private final Subject<Boolean> mIsLoadingEmitter;

    public PagedItemChecklistCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
        mItemChecklistStatesSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mItemChecklistStatesEmitter = mItemChecklistStatesSubject.toSerialized();
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
                    ArrayList<ItemChecklistState> results = new ArrayList<>();
                    for (ItemChecklist itemChecklist : mItemChecklistDao.searchItemChecklist(search)) {
                        ItemChecklistState state = mItemChecklistDao.findItemChecklistStateById(itemChecklist.id);
                        if (state != null) {
                            results.add(state);
                        }
                    }
                    mItemChecklistStatesEmitter.onNext(results);
                } catch (Throwable throwable) {
                    mItemChecklistStatesEmitter.onNext(new ArrayList<>());
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
                mItemChecklistStatesEmitter.onNext(
                        loadItems());
            } catch (Throwable throwable) {
                mItemChecklistStatesEmitter.onNext(mItemChecklistStatesSubject.getValue());
            } finally {
                mIsLoadingEmitter.onNext(false);
            }
        });
    }

    private ArrayList<ItemChecklistState> loadItems() {
        return new ArrayList<>(mItemChecklistDao.findItemChecklistWithLimit(mLimit, mQueryOrderBy));
    }

    public ArrayList<ItemChecklistState> getAllItems() {
        return mItemChecklistStatesSubject.getValue();
    }

    public Flowable<ArrayList<ItemChecklistState>> getItemChecklistsFlow() {
        return Flowable.fromObservable(mItemChecklistStatesEmitter, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingEmitter, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 100;
    }

    public void setQueryOrderBy(ItemChecklistDao.QueryOrderBy queryOrderBy) {
        mQueryOrderBy = queryOrderBy;
        load();
    }
}
