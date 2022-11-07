package m.co.rh.id.a_personal_stuff.app.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.aprovider.Provider;

public class PagedItemCmd {
    private Context mAppContext;
    private ExecutorService mExecutorService;
    private ItemDao mItemDao;
    private int mLimit;
    private String mSearch;
    private final BehaviorSubject<ArrayList<ItemState>> mItemStatesSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final BehaviorSubject<Set<Long>> mSelectedIdsSubject;

    public PagedItemCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mItemDao = provider.get(ItemDao.class);
        mItemStatesSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(false);
        mSelectedIdsSubject = BehaviorSubject.createDefault(new LinkedHashSet<>());
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
                    Future<List<Long>> itemTagSearchResult = mExecutorService.submit(
                            () -> {
                                List<ItemTag> itemTags = mItemDao.searchItemTag(search);
                                List<Long> itemIds = new ArrayList<>();
                                if (!itemTags.isEmpty()) {
                                    for (ItemTag itemTag : itemTags) {
                                        itemIds.add(itemTag.itemId);
                                    }
                                }

                                return itemIds;
                            }
                    );
                    Future<List<Long>> itemSearchResult = mExecutorService.submit(
                            () -> {
                                List<Item> items = mItemDao.searchItem(search);
                                List<Long> itemIds = new ArrayList<>();
                                if (!items.isEmpty()) {
                                    for (Item item : items) {
                                        itemIds.add(item.id);
                                    }
                                }
                                return itemIds;
                            }
                    );
                    Set<Long> itemIds = new LinkedHashSet<>();
                    itemIds.addAll(itemTagSearchResult.get());
                    itemIds.addAll(itemSearchResult.get());
                    List<ItemState> itemStates =
                            mItemDao.findItemStatesByIds(new ArrayList<>(itemIds));
                    mItemStatesSubject.onNext(new ArrayList<>(itemStates));
                } catch (Throwable throwable) {
                    mItemStatesSubject.onError(throwable);
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
        executeLoad(this::loadItems);
    }

    private void executeLoad(Callable<ArrayList<ItemState>> callable) {
        mExecutorService.execute(() -> {
            mIsLoadingSubject.onNext(true);
            try {
                mItemStatesSubject.onNext(
                        callable.call());
            } catch (Throwable throwable) {
                mItemStatesSubject.onError(throwable);
            } finally {
                mIsLoadingSubject.onNext(false);
            }
        });
    }

    private ArrayList<ItemState> loadItems() {
        return new ArrayList<>(mItemDao.findItemStateWithLimit(mLimit));
    }

    public ArrayList<ItemState> getAllItems() {
        return mItemStatesSubject.getValue();
    }

    public Flowable<ArrayList<ItemState>> getItemsFlow() {
        return Flowable.fromObservable(mItemStatesSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 100;
    }

    public void refreshWithItemId(long itemId) {
        executeLoad(() -> new ArrayList<>(
                mItemDao.findItemStatesByIds(Collections.singletonList(itemId))
        ));
    }
}
