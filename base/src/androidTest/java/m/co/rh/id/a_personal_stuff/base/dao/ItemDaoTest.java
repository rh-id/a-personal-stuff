package m.co.rh.id.a_personal_stuff.base.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.math.BigDecimal;

import m.co.rh.id.a_personal_stuff.base.dao.ItemDao.QueryOrderBy;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;

/**
 * Pins the exact returned ordering of every ItemDao sort-matrix entry
 * (all {@link QueryOrderBy} keys plus the null/default composite) across
 * the three query families, by comparing the DAO output against a
 * comparator-derived expected order. Runs against the CURRENT
 * implementation first as the baseline; must keep passing identically
 * after any equivalent refactor (e.g. the @RawQuery consolidation).
 * <p>
 * Every item has a DISTINCT created/updated/expired value (except one
 * NULL expired, which SQLite sorts first on ASC / last on DESC) so each
 * single-key order is total and deterministic.
 * <p>
 * Pre-refactor baseline quirk pinned here: the ids family with a NULL
 * key ({@code findItemByIds}) had NO ORDER BY (unspecified order) and is
 * pinned membership-only; post-refactor that cell returns the composite
 * order - the membership assertion holds under both.
 * <p>
 * Also hosts the {@code assembleItemStates}/{@code chunkIds} static-helper
 * tests moved from the JVM source set per owner decision.
 */
@RunWith(AndroidJUnit4.class)
public class ItemDaoTest {

    private static final int ITEM_COUNT = 8;
    private static final long BASE_MILLIS = 1_700_000_000_000L;
    private static final long STEP_MILLIS = 1_000_000L;
    private static final int TRUNCATED_LIMIT = 5;

    // rank per item index; higher rank = later date. Insertion order is
    // deliberately different from every sort order.
    private static final int[] CREATED_RANK = {5, 0, 7, 2, 6, 1, 4, 3};
    private static final int[] UPDATED_RANK = {2, 6, 0, 7, 3, 5, 1, 4};
    private static final int[] EXPIRED_RANK = {-1, 4, 1, 6, 0, 7, 2, 5}; // -1 => NULL
    private static final int[] INSERT_ORDER = {3, 7, 1, 5, 0, 6, 2, 4};

    private AppDatabase mAppDb;
    private ItemDao mItemDao;
    private List<Item> mInsertedItems;
    private List<Long> mItemIds;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mAppDb = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        mItemDao = mAppDb.itemDao();
        insertDefaultDataset();
    }

    @After
    public void tearDown() {
        if (mAppDb != null) {
            mAppDb.close();
        }
    }

    private void insertDefaultDataset() {
        mInsertedItems = new ArrayList<>(ITEM_COUNT);
        mItemIds = new ArrayList<>(ITEM_COUNT);
        for (int i = 0; i < ITEM_COUNT; i++) {
            Item item = new Item();
            item.name = "item-" + i;
            item.amount = 1;
            item.createdDateTime = date(CREATED_RANK[i]);
            item.updatedDateTime = date(UPDATED_RANK[i]);
            item.expiredDateTime = EXPIRED_RANK[i] < 0 ? null : date(EXPIRED_RANK[i]);
            mInsertedItems.add(item);
        }
        for (int index : INSERT_ORDER) {
            Item item = mInsertedItems.get(index);
            insertItem(item);
            mItemIds.add(item.id);
        }
    }

    private void insertItem(Item item) {
        ItemState itemState = new ItemState();
        itemState.updateItem(item);
        mItemDao.insertItem(itemState);
    }

    private static Date date(int rank) {
        return new Date(BASE_MILLIS + rank * STEP_MILLIS);
    }

    // SQLite treats NULL as smaller than any value: first on ASC, last on DESC.
    private static int compareNullsFirst(Date a, Date b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    private static final Comparator<Item> EXPIRED_ASC =
            (a, b) -> compareNullsFirst(a.expiredDateTime, b.expiredDateTime);
    private static final Comparator<Item> EXPIRED_DESC =
            (a, b) -> compareNullsFirst(b.expiredDateTime, a.expiredDateTime);
    private static final Comparator<Item> UPDATED_ASC =
            (a, b) -> compareNullsFirst(a.updatedDateTime, b.updatedDateTime);
    private static final Comparator<Item> UPDATED_DESC =
            (a, b) -> compareNullsFirst(b.updatedDateTime, a.updatedDateTime);
    private static final Comparator<Item> CREATED_ASC =
            (a, b) -> compareNullsFirst(a.createdDateTime, b.createdDateTime);
    private static final Comparator<Item> CREATED_DESC =
            (a, b) -> compareNullsFirst(b.createdDateTime, a.createdDateTime);

    // default (null) order: expired DESC, updated DESC, created DESC
    private static final Comparator<Item> DEFAULT_COMPOSITE = (a, b) -> {
        int result = compareNullsFirst(b.expiredDateTime, a.expiredDateTime);
        if (result == 0) result = compareNullsFirst(b.updatedDateTime, a.updatedDateTime);
        if (result == 0) result = compareNullsFirst(b.createdDateTime, a.createdDateTime);
        return result;
    };

    private List<String> expectedNames(Comparator<Item> comparator) {
        List<Item> items = new ArrayList<>(mInsertedItems);
        items.sort(comparator);
        return names(items);
    }

    private static List<String> names(List<Item> items) {
        List<String> names = new ArrayList<>(items.size());
        for (Item item : items) {
            names.add(item.name);
        }
        return names;
    }

    private static List<String> actualNames(List<ItemState> itemStates) {
        List<String> names = new ArrayList<>(itemStates.size());
        for (ItemState itemState : itemStates) {
            names.add(itemState.getItem().name);
        }
        return names;
    }

    /**
     * Asserts the exact expected order from all THREE families, plus the
     * LIMIT respected in the WithLimit families (1 and 3). For explicit
     * keys every family applies the single-key ORDER BY, so exact order
     * is pinned.
     */
    private void assertAllFamiliesOrder(QueryOrderBy queryOrderBy, List<String> expected) {
        assertEquals(expected,
                actualNames(mItemDao.findItemStateWithLimit(ITEM_COUNT + 10, queryOrderBy)));
        assertEquals(expected.subList(0, TRUNCATED_LIMIT),
                actualNames(mItemDao.findItemStateWithLimit(TRUNCATED_LIMIT, queryOrderBy)));
        assertEquals(expected,
                actualNames(mItemDao.findItemStatesByIds(mItemIds, queryOrderBy)));
        assertEquals(expected,
                actualNames(mItemDao.findItemStateByIdsWithLimit(
                        mItemIds, queryOrderBy, ITEM_COUNT + 10)));
        assertEquals(expected.subList(0, TRUNCATED_LIMIT),
                actualNames(mItemDao.findItemStateByIdsWithLimit(
                        mItemIds, queryOrderBy, TRUNCATED_LIMIT)));
    }

    /**
     * Same as {@link #assertAllFamiliesOrder} but for the null/default key:
     * families 1 and 3 apply the composite ORDER BY (exact order pinned),
     * while family 2 ({@code findItemByIds}) had NO ORDER BY in the
     * pre-refactor baseline (unspecified order), so only membership is
     * pinned there; post-refactor it returns the composite order - the
     * membership assertion holds under both.
     */
    private void assertNullOrderFamilies(List<String> expected) {
        assertEquals(expected,
                actualNames(mItemDao.findItemStateWithLimit(ITEM_COUNT + 10, null)));
        assertSameNames(expected,
                actualNames(mItemDao.findItemStatesByIds(mItemIds, null)));
        assertEquals(expected,
                actualNames(mItemDao.findItemStateByIdsWithLimit(
                        mItemIds, null, ITEM_COUNT + 10)));
        assertEquals(expected.subList(0, TRUNCATED_LIMIT),
                actualNames(mItemDao.findItemStateByIdsWithLimit(
                        mItemIds, null, TRUNCATED_LIMIT)));
    }

    private static void assertSameNames(List<String> expected, List<String> actual) {
        List<String> expectedSorted = new ArrayList<>(expected);
        List<String> actualSorted = new ArrayList<>(actual);
        Collections.sort(expectedSorted);
        Collections.sort(actualSorted);
        assertEquals(expectedSorted, actualSorted);
    }

    @Test
    public void expiredDateTimeAsc() {
        assertAllFamiliesOrder(QueryOrderBy.EXPIRED_DATE_TIME_ASC,
                expectedNames(EXPIRED_ASC));
    }

    @Test
    public void expiredDateTimeDesc() {
        assertAllFamiliesOrder(QueryOrderBy.EXPIRED_DATE_TIME_DESC,
                expectedNames(EXPIRED_DESC));
    }

    @Test
    public void updatedDateTimeAsc() {
        assertAllFamiliesOrder(QueryOrderBy.UPDATED_DATE_TIME_ASC,
                expectedNames(UPDATED_ASC));
    }

    @Test
    public void updatedDateTimeDesc() {
        assertAllFamiliesOrder(QueryOrderBy.UPDATED_DATE_TIME_DESC,
                expectedNames(UPDATED_DESC));
    }

    @Test
    public void createdDateTimeAsc() {
        assertAllFamiliesOrder(QueryOrderBy.CREATED_DATE_TIME_ASC,
                expectedNames(CREATED_ASC));
    }

    @Test
    public void createdDateTimeDesc() {
        assertAllFamiliesOrder(QueryOrderBy.CREATED_DATE_TIME_DESC,
                expectedNames(CREATED_DESC));
    }

    @Test
    public void nullOrderByUsesDefaultComposite() {
        assertNullOrderFamilies(expectedNames(DEFAULT_COMPOSITE));
    }

    /**
     * The main dataset's expired values are all distinct, so the composite's
     * tiebreakers would never engage there. This dedicated dataset adds
     * rows sharing expired values pairwise to pin "expired DESC, updated
     * DESC, created DESC" fully across the merged table. Single-key orders
     * are intentionally NOT asserted on the shared-expired rows (a tied
     * single key has an unspecified SQLite order).
     */
    @Test
    public void defaultCompositeEngagesUpdatedAndCreatedTiebreakers() {
        long sharedExpired = BASE_MILLIS + 10 * STEP_MILLIS;
        Item tiedHighUpdated = item("tie-updated-high",
                date(0), date(20), new Date(sharedExpired));
        Item tiedLowUpdatedHighCreated = item("tie-created-high",
                date(2), date(10), new Date(sharedExpired));
        Item tiedLowUpdatedLowCreated = item("tie-created-low",
                date(1), date(10), new Date(sharedExpired));
        Item laterExpired = item("later-expired",
                date(3), date(0), date(11));
        Item nullExpired = item("null-expired-last",
                date(4), date(30), null);
        // insertion order deliberately shuffled
        List<Item> tieItems = Arrays.asList(
                tiedLowUpdatedLowCreated, nullExpired, tiedHighUpdated,
                laterExpired, tiedLowUpdatedHighCreated);
        List<Long> tieIds = new ArrayList<>(tieItems.size());
        for (Item item : tieItems) {
            insertItem(item);
            tieIds.add(item.id);
        }

        // expected over the WHOLE merged table (default dataset + tie rows)
        List<Item> allItems = new ArrayList<>(mInsertedItems);
        allItems.addAll(tieItems);
        allItems.sort(DEFAULT_COMPOSITE);
        List<String> expectedMerged = names(allItems);

        // family 1 queries the whole table: merged expectation
        assertEquals(expectedMerged, actualNames(
                mItemDao.findItemStateWithLimit(allItems.size() + 10, null)));
        // ids family with null key: membership only (pre-refactor baseline
        // had no ORDER BY here; post-refactor it returns the composite order)
        assertSameNames(names(tieItems),
                actualNames(mItemDao.findItemStatesByIds(tieIds, null)));
        // families restricted to tieIds: tie rows only, composite-sorted
        List<Item> tieSorted = new ArrayList<>(tieItems);
        tieSorted.sort(DEFAULT_COMPOSITE);
        List<String> expectedTie = names(tieSorted);
        assertEquals(expectedTie, actualNames(
                mItemDao.findItemStateByIdsWithLimit(tieIds, null, allItems.size() + 10)));
        assertEquals(expectedTie.subList(0, 3), actualNames(
                mItemDao.findItemStateByIdsWithLimit(tieIds, null, 3)));
    }

    private static Item item(String name, Date created, Date updated, Date expired) {
        Item item = new Item();
        item.name = name;
        item.amount = 1;
        item.createdDateTime = created;
        item.updatedDateTime = updated;
        item.expiredDateTime = expired;
        return item;
    }

    // archived-flag query family tests

    private void archiveItem(Item item) {
        item.archived = true;
        ItemState itemState = new ItemState();
        itemState.updateItem(item);
        mItemDao.updateItem(itemState);
    }

    /**
     * Pins the archive query-family split: the ACTIVE-ONLY family
     * (paged list, search, COUNT) must exclude an archived row, the
     * ARCHIVED family must return exactly it, and the UNFILTERED family
     * (all-states list, COUNT, findAllItems, id lookups — used by
     * backups/exports and name/id resolution) must keep seeing it.
     */
    @Test
    public void activeQueriesExcludeArchivedWhileArchivedAndUnfilteredQueriesIncludeThem() {
        Item archivedItem = mInsertedItems.get(3);
        archiveItem(archivedItem);
        long archivedId = archivedItem.id;

        // ACTIVE-ONLY family: the archived row is gone
        List<String> activeNames = actualNames(
                mItemDao.findItemStateWithLimit(ITEM_COUNT + 10, null));
        assertFalse("archived row must not surface in the active list",
                activeNames.contains(archivedItem.name));
        assertEquals(ITEM_COUNT - 1, activeNames.size());
        assertEquals(ITEM_COUNT - 1, mItemDao.countItems());
        assertEquals(ITEM_COUNT - 1, mItemDao.searchItem("item").size());
        assertEquals(0, mItemDao.searchItem("item-3").size());

        // ARCHIVED family: exactly the archived row
        List<String> archivedNames = actualNames(
                mItemDao.findArchivedItemStatesWithLimit(ITEM_COUNT + 10, null));
        assertEquals(Collections.singletonList(archivedItem.name), archivedNames);
        assertEquals(1, mItemDao.searchArchivedItem("item").size());
        assertEquals(archivedItem.name, mItemDao.searchArchivedItem("item-3").get(0).name);

        // UNFILTERED family: the archived row stays a row and resolvable
        assertEquals(ITEM_COUNT,
                mItemDao.findAllItemStatesWithLimit(ITEM_COUNT + 10, null).size());
        assertEquals(ITEM_COUNT, mItemDao.countAllItems());
        assertEquals(ITEM_COUNT, mItemDao.findAllItems().size());
        assertTrue(mItemDao.getAllItemIds().contains(archivedId));
        assertEquals(1, mItemDao.findItemStatesByIds(
                Collections.singletonList(archivedId)).size());
        assertNotNull(mItemDao.findItemById(archivedId));
    }

    /**
     * The aggregates and alert candidates (SUM, expiry, min-amount) are
     * active-only: archived items must not count toward the dashboard value
     * nor raise expiry/low-stock alerts. The default dataset rows have null
     * prices / no expiry at epoch / no min_amount, so they contribute nothing
     * to any of the assertions.
     */
    @Test
    public void sumsAndAlertCandidateQueriesExcludeArchivedItems() {
        Date epoch = new Date(0L);
        Item active = item("active-priced", date(0), date(0), epoch);
        active.price = new BigDecimal("2.00");
        active.minAmount = 1;
        insertItem(active);
        Item archived = item("archived-priced", date(1), date(1), epoch);
        archived.price = new BigDecimal("3.00");
        archived.minAmount = 1;
        insertItem(archived);
        archiveItem(archived);

        assertEquals(2.0, mItemDao.sumInventoryValue(), 0.0001);
        assertEquals(5.0, mItemDao.sumInventoryValueIncludingArchived(), 0.0001);

        // epoch expiry: only the two seeded items expire at/before this cutoff
        List<Item> expiring = mItemDao.findItemsExpiringBefore(new Date(1_000L));
        assertEquals(1, expiring.size());
        assertEquals("active-priced", expiring.get(0).name);
        assertEquals(1, mItemDao.countItemsExpiringBefore(new Date(1_000L)));

        List<Item> minAmountItems = mItemDao.findItemsWithMinAmount();
        assertEquals(1, minAmountItems.size());
        assertEquals("active-priced", minAmountItems.get(0).name);
    }

    // barcode exact-match query family tests

    private Item itemWithBarcode(String name, String barcode, Date created) {
        Item item = item(name, created, created, null);
        item.barcode = barcode;
        return item;
    }

    /**
     * Pins the scan-to-recall barcode lookup: exact string equality only
     * ("123" must NOT match "0123" or "1234"), with the plain item list and
     * the assembled state list returning the same rows.
     */
    @Test
    public void findItemsByBarcodeMatchesExactStringOnly() {
        Item exact = itemWithBarcode("exact", "123", date(0));
        insertItem(exact);
        insertItem(itemWithBarcode("padded-prefix", "0123", date(1)));
        insertItem(itemWithBarcode("extended", "1234", date(2)));

        List<Item> items = mItemDao.findItemsByBarcode("123");
        assertEquals(1, items.size());
        assertEquals("exact", items.get(0).name);

        List<ItemState> itemStates = mItemDao.findItemStatesByBarcode("123");
        assertEquals(1, itemStates.size());
        assertEquals(exact.id, itemStates.get(0).getItemId());
        assertEquals("exact", itemStates.get(0).getItemName());
    }

    /**
     * The scan-to-recall recall target must be the most recently created
     * item carrying the barcode: created_date_time DESC.
     */
    @Test
    public void findItemsByBarcodeOrdersMostRecentCreatedFirst() {
        Item older = itemWithBarcode("older", "777", date(0));
        Item newer = itemWithBarcode("newer", "777", date(5));
        insertItem(older);
        insertItem(newer);

        List<ItemState> itemStates = mItemDao.findItemStatesByBarcode("777");
        assertEquals(2, itemStates.size());
        assertEquals("newer", itemStates.get(0).getItemName());
        assertEquals("older", itemStates.get(1).getItemName());
    }

    /**
     * Same createdDateTime falls back to id DESC (rows inserted later / with
     * the higher auto id surface first).
     */
    @Test
    public void findItemsByBarcodeBreaksCreatedTiesByHigherIdFirst() {
        Item first = itemWithBarcode("first", "888", date(3));
        Item second = itemWithBarcode("second", "888", date(3));
        insertItem(first); // lower auto id
        insertItem(second); // higher auto id

        List<ItemState> itemStates = mItemDao.findItemStatesByBarcode("888");
        assertEquals(2, itemStates.size());
        assertEquals("second", itemStates.get(0).getItemName());
        assertEquals("first", itemStates.get(1).getItemName());
    }

    @Test
    public void findItemsByBarcodeWithNoMatchReturnsEmptyList() {
        insertItem(itemWithBarcode("exact", "123", date(0)));

        assertTrue(mItemDao.findItemsByBarcode("999").isEmpty());
        assertTrue(mItemDao.findItemStatesByBarcode("999").isEmpty());
    }

    /**
     * Archived items stay recallable: the barcode lookup is intentionally
     * NOT archive-filtered, and the archive boundary test above must not
     * leak into this family.
     */
    @Test
    public void findItemsByBarcodeIncludesArchivedItems() {
        Item active = itemWithBarcode("active", "555", date(1));
        Item archived = itemWithBarcode("archived", "555", date(2));
        insertItem(active);
        insertItem(archived);
        archiveItem(archived);

        List<ItemState> itemStates = mItemDao.findItemStatesByBarcode("555");
        assertEquals(2, itemStates.size());
        assertEquals("archived", itemStates.get(0).getItemName());
        assertEquals("active", itemStates.get(1).getItemName());
    }

    // static helper tests - moved from the JVM source set per owner decision

    @Test
    public void groupsImagesAndTagsOntoTheirItemsPreservingInputOrder() {
        List<Item> items = new ArrayList<>(Arrays.asList(
                item(1L, "Beans"), item(2L, "Rice"), item(3L, "Noodles")));
        // deliberately unsorted image input to prove grouping is by itemId,
        // not by list position
        List<ItemImage> images = new ArrayList<>(Arrays.asList(
                image(11L, 3L, "noodles.jpg"),
                image(10L, 1L, "beans_a.jpg"),
                image(12L, 1L, "beans_b.jpg")));
        List<ItemTag> tags = new ArrayList<>(Collections.singletonList(
                tag(21L, 2L, "pantry")));

        List<ItemState> itemStates = ItemDao.assembleItemStates(items, images, tags);

        assertEquals(3, itemStates.size());
        // items-list order is preserved
        assertEquals("Beans", itemStates.get(0).getItemName());
        assertEquals("Rice", itemStates.get(1).getItemName());
        assertEquals("Noodles", itemStates.get(2).getItemName());

        List<ItemImage> beansImages = itemStates.get(0).getItemImages();
        assertEquals(2, beansImages.size());
        assertEquals("beans_a.jpg", beansImages.get(0).fileName);
        assertEquals("beans_b.jpg", beansImages.get(1).fileName);
        assertTrue(itemStates.get(0).getItemTags().isEmpty());

        List<ItemImage> riceImages = itemStates.get(1).getItemImages();
        assertTrue(riceImages.isEmpty());
        assertEquals(1, itemStates.get(1).getItemTags().size());
        assertTrue(itemStates.get(1).getItemTags().contains(tag(21L, 2L, "pantry")));

        List<ItemImage> noodlesImages = itemStates.get(2).getItemImages();
        assertEquals(1, noodlesImages.size());
        assertEquals("noodles.jpg", noodlesImages.get(0).fileName);
        assertTrue(itemStates.get(2).getItemTags().isEmpty());
    }

    @Test
    public void itemsWithoutImagesOrTagsKeepEmptyChildLists() {
        List<Item> items = new ArrayList<>(Collections.singletonList(item(1L, "Beans")));

        List<ItemState> itemStates = ItemDao.assembleItemStates(items,
                new ArrayList<>(), new ArrayList<>());

        assertEquals(1, itemStates.size());
        assertEquals("Beans", itemStates.get(0).getItemName());
        assertTrue(itemStates.get(0).getItemImages().isEmpty());
        assertTrue(itemStates.get(0).getItemTags().isEmpty());
    }

    @Test
    public void emptyItemsYieldEmptyList() {
        List<ItemState> itemStates = ItemDao.assembleItemStates(
                new ArrayList<>(),
                new ArrayList<>(Collections.singletonList(image(10L, 1L, "orphan.jpg"))),
                new ArrayList<>());

        assertTrue(itemStates.isEmpty());
    }

    private static Item item(long id, String name) {
        Item item = new Item();
        item.id = id;
        item.name = name;
        item.amount = 1;
        item.createdDateTime = new Date(0L);
        item.updatedDateTime = new Date(0L);
        return item;
    }

    private static ItemImage image(long id, long itemId, String fileName) {
        ItemImage itemImage = new ItemImage();
        itemImage.id = id;
        itemImage.itemId = itemId;
        itemImage.fileName = fileName;
        itemImage.createdDateTime = new Date(0L);
        return itemImage;
    }

    private static ItemTag tag(long id, long itemId, String tag) {
        ItemTag itemTag = new ItemTag();
        itemTag.id = id;
        itemTag.itemId = itemId;
        itemTag.tag = tag;
        itemTag.createdDateTime = new Date(0L);
        return itemTag;
    }

    @Test
    public void chunkIdsWithEmptyListYieldsNoChunks() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(0));
        assertEquals(0, chunks.size());
    }

    @Test
    public void chunkIdsWithSingleIdYieldsOneChunk() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(1));
        assertChunks(chunks, 1, 1);
    }

    @Test
    public void chunkIdsJustUnderLimitYieldsOneChunk() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(499));
        assertChunks(chunks, 1, 499);
    }

    @Test
    public void chunkIdsAtExactLimitYieldsOneChunk() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(500));
        assertChunks(chunks, 1, 500);
    }

    @Test
    public void chunkIdsJustOverLimitYieldsTwoChunks() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(501));
        assertChunks(chunks, 2, 501);
    }

    @Test
    public void chunkIdsJustUnderTwoLimitsYieldsTwoChunks() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(999));
        assertChunks(chunks, 2, 999);
    }

    @Test
    public void chunkIdsAtTwoExactLimitsYieldsTwoChunks() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(1000));
        assertChunks(chunks, 2, 1000);
    }

    @Test
    public void chunkIdsAtMaxPageLimitYieldsFourChunks() {
        // 1600 is the largest page size reachable via PagedItemCmd#loadNextPage
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(1600));
        assertChunks(chunks, 4, 1600);
    }

    /**
     * Verifies the generic chunking contract for an input of ids 1..totalIds:
     * the explicitly expected number of chunks, every chunk except the last
     * holding exactly the 500-id bind cap, the last chunk holding 1..500 ids,
     * and the concatenation reproducing the original list in order without
     * losses or duplicates.
     */
    private static void assertChunks(List<List<Long>> chunks, int expectedChunkCount, int totalIds) {
        assertEquals(expectedChunkCount, chunks.size());
        if (expectedChunkCount == 0) {
            return;
        }
        List<Long> concatenated = new ArrayList<>();
        for (int c = 0; c < chunks.size(); c++) {
            List<Long> chunk = chunks.get(c);
            if (c < chunks.size() - 1) {
                assertEquals("every chunk except the last must hold the full 500-id cap",
                        500, chunk.size());
            } else {
                assertTrue("last chunk must hold 1..500 ids, was " + chunk.size(),
                        chunk.size() >= 1 && chunk.size() <= 500);
            }
            concatenated.addAll(chunk);
        }
        assertEquals(totalIds, concatenated.size());
        for (int i = 0; i < totalIds; i++) {
            assertEquals("ids must survive in order, without losses or duplicates",
                    Long.valueOf(i + 1), concatenated.get(i));
        }
    }

    private static List<Long> buildIds(int count) {
        List<Long> ids = new ArrayList<>(count);
        for (long id = 1; id <= count; id++) {
            ids.add(id);
        }
        return ids;
    }
}
