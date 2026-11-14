package m.co.rh.id.a_personal_stuff.app.provider.command;

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
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class PagedItemCmd {
    private static final String TAG = PagedItemCmd.class.getName();

    private ExecutorService mExecutorService;
    private ItemDao mItemDao;
    private ILogger mLogger;
    private int mLimit;
    private String mSearch;
    private ItemDao.QueryOrderBy mQueryOrderBy;
    /**
     * Nullable — when non-null, only these items are shown (filtered
     * items-list mode). Null means no filter. An EMPTY list is also a valid
     * state: it filters everything out (used when all matching alerts have
     * resolved) and must never reach a Room IN () query, which throws on an
     * empty collection.
     */
    private List<Long> mFilterItemIds;
    /**
     * When true this cmd serves the ARCHIVED list (archived = 1 rows only)
     * instead of the active one (archived = 0). Must be set BEFORE the first
     * refresh (each page sub-provider owns its own PagedItemCmd instance, so
     * the flag never needs to flip mid-life).
     */
    private boolean mArchivedOnly;
    private final BehaviorSubject<ArrayList<ItemState>> mItemStatesSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final Subject<ArrayList<ItemState>> mItemStatesEmitter;
    private final Subject<Boolean> mIsLoadingEmitter;

    public PagedItemCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mItemDao = provider.get(ItemDao.class);
        mLogger = provider.get(ILogger.class);
        mItemStatesSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mItemStatesEmitter = mItemStatesSubject.toSerialized();
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
                                // the text query is mode-aware: searchItem is
                                // active-only, so the archived page must search
                                // with searchArchivedItem instead or it could
                                // never match by name/description/barcode
                                List<Item> items = mArchivedOnly
                                        ? mItemDao.searchArchivedItem(search)
                                        : mItemDao.searchItem(search);
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
                    if (mFilterItemIds != null) {
                        // a search within filtered mode cannot escape the filtered item set
                        itemIds.retainAll(mFilterItemIds);
                        if (itemIds.isEmpty()) {
                            mItemStatesEmitter.onNext(new ArrayList<>());
                            return;
                        }
                    }
                    List<ItemState> itemStates =
                            mItemDao.findItemStateByIdsWithLimit(new ArrayList<>(itemIds), mQueryOrderBy, mLimit);
                    // The tag-search union is unfiltered by design (searchItemTag
                    // has no archive filter), so the post-filter below is what
                    // keeps a search from leaking rows across the archive
                    // boundary. It runs AFTER the LIMIT, so an archived-mode page
                    // can underfill until the next refresh — same accepted class
                    // of behavior as deletes not rebalancing loaded pages.
                    mItemStatesEmitter.onNext(new ArrayList<>(filterByArchiveMode(itemStates)));
                } catch (Throwable throwable) {
                    mLogger.e(TAG, throwable.getMessage(), throwable);
                    mItemStatesEmitter.onNext(new ArrayList<>());
                } finally {
                    mIsLoadingEmitter.onNext(false);
                }
            }
        });
    }

    public void loadNextPage() {
        if (getAllItems().size() < mLimit) {
            return;
        }
        mLimit += mLimit;
        refresh();
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
            mIsLoadingEmitter.onNext(true);
            try {
                mItemStatesEmitter.onNext(
                        callable.call());
            } catch (Throwable throwable) {
                mLogger.e(TAG, throwable.getMessage(), throwable);
                mItemStatesEmitter.onNext(mItemStatesSubject.getValue());
            } finally {
                mIsLoadingEmitter.onNext(false);
            }
        });
    }

    private ArrayList<ItemState> loadItems() {
        if (mArchivedOnly) {
            // archived-items page: archived rows only
            return new ArrayList<>(mItemDao.findArchivedItemStatesWithLimit(mLimit, mQueryOrderBy));
        }
        if (mFilterItemIds != null) {
            // an empty filter matches nothing; Room's IN () throws on an empty
            // list, so short-circuit without touching the DAO
            if (mFilterItemIds.isEmpty()) {
                return new ArrayList<>();
            }
            // filtered mode: only these items regardless of the page limit
            return new ArrayList<>(mItemDao.findItemStatesByIds(
                    new ArrayList<>(mFilterItemIds), mQueryOrderBy));
        }
        return new ArrayList<>(mItemDao.findItemStateWithLimit(mLimit, mQueryOrderBy));
    }

    public ArrayList<ItemState> getAllItems() {
        return mItemStatesSubject.getValue();
    }

    public Flowable<ArrayList<ItemState>> getItemsFlow() {
        return Flowable.fromObservable(mItemStatesEmitter, BackpressureStrategy.BUFFER);
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingEmitter, BackpressureStrategy.BUFFER);
    }

    private void resetPage() {
        mLimit = 100;
    }

    /**
     * Optional item-ID-set filter (filtered items-list mode). Null clears the
     * filter and restores normal behavior; an EMPTY list filters everything
     * out. A defensive copy is stored so later caller-side mutations cannot
     * leak into an in-flight load. Callers refresh after changing it.
     */
    public void setItemFilter(List<Long> filterItemIds) {
        if (filterItemIds == null) {
            mFilterItemIds = null;
        } else {
            mFilterItemIds = new ArrayList<>(filterItemIds);
        }
    }

    /**
     * Switches this cmd between the active list (false) and the archived
     * list (true), see {@link #mArchivedOnly}. Must be set before the first
     * refresh; no refresh is triggered here.
     */
    public void setArchivedOnly(boolean archivedOnly) {
        mArchivedOnly = archivedOnly;
    }

    /**
     * Keeps only the rows matching this cmd's archive side: active mode
     * drops archived rows, archived mode keeps archived rows only. Applied
     * to search results in BOTH modes because the tag-search union
     * ({@code searchItemTag}) is unfiltered by design.
     */
    private List<ItemState> filterByArchiveMode(List<ItemState> itemStates) {
        if (itemStates.isEmpty()) {
            return itemStates;
        }
        List<ItemState> filtered = new ArrayList<>(itemStates.size());
        for (ItemState itemState : itemStates) {
            if (itemState.isArchived() == mArchivedOnly) {
                filtered.add(itemState);
            }
        }
        return filtered;
    }

    public void refreshWithItemId(long itemId) {
        executeLoad(() -> new ArrayList<>(
                mItemDao.findItemStatesByIds(Collections.singletonList(itemId), mQueryOrderBy)
        ));
    }

    private void orderItem(ItemDao.QueryOrderBy queryOrderBy) {
        mQueryOrderBy = queryOrderBy;
        refresh();
    }

    public void orderItemByExpiredTimeDate() {
        orderItem(ItemDao.QueryOrderBy.EXPIRED_DATE_TIME_ASC);
    }

    public void orderItemByExpiredDateTimeDesc() {
        orderItem(ItemDao.QueryOrderBy.EXPIRED_DATE_TIME_DESC);
    }

    public void orderItemByUpdatedDateTime() {
        orderItem(ItemDao.QueryOrderBy.UPDATED_DATE_TIME_ASC);
    }

    public void orderItemByUpdatedDateTimeDesc() {
        orderItem(ItemDao.QueryOrderBy.UPDATED_DATE_TIME_DESC);
    }

    public void orderItemByCreatedDateTime() {
        orderItem(ItemDao.QueryOrderBy.CREATED_DATE_TIME_ASC);
    }

    public void orderItemByCreatedDateTimeDesc() {
        orderItem(ItemDao.QueryOrderBy.CREATED_DATE_TIME_DESC);
    }

    public void resetOrder() {
        orderItem(null);
    }
}
