package m.co.rh.id.a_personal_stuff.item_purchase.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.aprovider.Provider;

public class PagedItemPurchaseCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private ItemPurchaseDao mItemPurchaseDao;
    private long mItemId;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<ItemPurchaseState>> mItemPurchaseStatesSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final Subject<ArrayList<ItemPurchaseState>> mItemPurchaseStatesEmitter;
    private final Subject<Boolean> mIsLoadingEmitter;

    public PagedItemPurchaseCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
        mItemPurchaseStatesSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mItemPurchaseStatesEmitter = mItemPurchaseStatesSubject.toSerialized();
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
                    mItemPurchaseStatesEmitter.onNext(new ArrayList<>(
                            mItemPurchaseDao.searchItemPurchaseStateByItemId(mItemId, search))
                    );
                } catch (Throwable throwable) {
                    mItemPurchaseStatesEmitter.onNext(new ArrayList<>());
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
                mItemPurchaseStatesEmitter.onNext(
                        loadItems());
            } catch (Throwable throwable) {
                mItemPurchaseStatesEmitter.onNext(mItemPurchaseStatesSubject.getValue());
            } finally {
                mIsLoadingEmitter.onNext(false);
            }
        });
    }

    private ArrayList<ItemPurchaseState> loadItems() {
        return new ArrayList<>(mItemPurchaseDao.findItemPurchaseStateByItemIdWithLimit(mItemId, mLimit));
    }

    public ArrayList<ItemPurchaseState> getAllItems() {
        return mItemPurchaseStatesSubject.getValue();
    }

    public Flowable<ArrayList<ItemPurchaseState>> getItemPurchasesFlow() {
        return Flowable.fromObservable(mItemPurchaseStatesEmitter, BackpressureStrategy.BUFFER);
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
