package m.co.rh.id.a_personal_stuff.item_checklist.dao;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao.QueryOrderBy;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.room.ItemChecklistDatabase;

/**
 * Pins the exact returned ordering of
 * {@link ItemChecklistDao#findItemChecklistWithLimit} for every
 * {@link QueryOrderBy} key plus the null/default composite
 * (updated DESC, created DESC), by comparing the DAO output against a
 * comparator-derived expected order. Runs against the CURRENT
 * implementation first as the baseline; must keep passing identically
 * after any equivalent refactor (e.g. the @RawQuery consolidation).
 * <p>
 * Every checklist has a DISTINCT created/updated value so each single-key
 * order is total and deterministic.
 */
@RunWith(AndroidJUnit4.class)
public class ItemChecklistDaoTest {

    private static final int CHECKLIST_COUNT = 8;
    private static final long BASE_MILLIS = 1_700_000_000_000L;
    private static final long STEP_MILLIS = 1_000_000L;
    private static final int TRUNCATED_LIMIT = 5;

    // rank per checklist index; higher rank = later date. Insertion order is
    // deliberately different from every sort order.
    private static final int[] CREATED_RANK = {5, 0, 7, 2, 6, 1, 4, 3};
    private static final int[] UPDATED_RANK = {2, 6, 0, 7, 3, 5, 1, 4};
    private static final int[] INSERT_ORDER = {3, 7, 1, 5, 0, 6, 2, 4};

    private ItemChecklistDatabase mDb;
    private ItemChecklistDao mItemChecklistDao;
    private List<ItemChecklist> mInsertedChecklists;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDb = Room.inMemoryDatabaseBuilder(context, ItemChecklistDatabase.class)
                .allowMainThreadQueries().build();
        mItemChecklistDao = mDb.itemChecklistDao();
        insertDefaultDataset();
    }

    @After
    public void tearDown() {
        if (mDb != null) {
            mDb.close();
        }
    }

    private void insertDefaultDataset() {
        mInsertedChecklists = new ArrayList<>(CHECKLIST_COUNT);
        for (int i = 0; i < CHECKLIST_COUNT; i++) {
            ItemChecklist itemChecklist = new ItemChecklist();
            itemChecklist.title = "checklist-" + i;
            itemChecklist.createdDateTime = date(CREATED_RANK[i]);
            itemChecklist.updatedDateTime = date(UPDATED_RANK[i]);
            mInsertedChecklists.add(itemChecklist);
        }
        for (int index : INSERT_ORDER) {
            insertChecklist(mInsertedChecklists.get(index));
        }
    }

    private void insertChecklist(ItemChecklist itemChecklist) {
        ItemChecklistState itemChecklistState = new ItemChecklistState();
        itemChecklistState.updateItemChecklist(itemChecklist);
        mItemChecklistDao.insertItemChecklist(itemChecklistState);
    }

    private static Date date(int rank) {
        return new Date(BASE_MILLIS + rank * STEP_MILLIS);
    }

    private static final Comparator<ItemChecklist> UPDATED_ASC =
            Comparator.comparing(itemChecklist -> itemChecklist.updatedDateTime);
    private static final Comparator<ItemChecklist> UPDATED_DESC =
            (a, b) -> b.updatedDateTime.compareTo(a.updatedDateTime);
    private static final Comparator<ItemChecklist> CREATED_ASC =
            Comparator.comparing(itemChecklist -> itemChecklist.createdDateTime);
    private static final Comparator<ItemChecklist> CREATED_DESC =
            (a, b) -> b.createdDateTime.compareTo(a.createdDateTime);

    // default (null) order: updated DESC, created DESC
    private static final Comparator<ItemChecklist> DEFAULT_COMPOSITE = (a, b) -> {
        int result = b.updatedDateTime.compareTo(a.updatedDateTime);
        if (result == 0) result = b.createdDateTime.compareTo(a.createdDateTime);
        return result;
    };

    private List<String> expectedTitles(Comparator<ItemChecklist> comparator) {
        List<ItemChecklist> itemChecklists = new ArrayList<>(mInsertedChecklists);
        itemChecklists.sort(comparator);
        return titles(itemChecklists);
    }

    private static List<String> titles(List<ItemChecklist> itemChecklists) {
        List<String> titles = new ArrayList<>(itemChecklists.size());
        for (ItemChecklist itemChecklist : itemChecklists) {
            titles.add(itemChecklist.title);
        }
        return titles;
    }

    private static List<String> actualTitles(List<ItemChecklistState> itemChecklistStates) {
        List<String> titles = new ArrayList<>(itemChecklistStates.size());
        for (ItemChecklistState itemChecklistState : itemChecklistStates) {
            titles.add(itemChecklistState.getTitle());
        }
        return titles;
    }

    /**
     * Asserts the exact expected order with a LIMIT larger than the table
     * (full result) and the LIMIT respected with a smaller value.
     */
    private void assertOrders(QueryOrderBy orderBy, List<String> expected) {
        assertEquals(expected, actualTitles(
                mItemChecklistDao.findItemChecklistWithLimit(CHECKLIST_COUNT + 10, orderBy)));
        assertEquals(expected.subList(0, TRUNCATED_LIMIT), actualTitles(
                mItemChecklistDao.findItemChecklistWithLimit(TRUNCATED_LIMIT, orderBy)));
    }

    @Test
    public void updatedDateTimeAsc() {
        assertOrders(QueryOrderBy.UPDATED_DATE_TIME_ASC, expectedTitles(UPDATED_ASC));
    }

    @Test
    public void updatedDateTimeDesc() {
        assertOrders(QueryOrderBy.UPDATED_DATE_TIME_DESC, expectedTitles(UPDATED_DESC));
    }

    @Test
    public void createdDateTimeAsc() {
        assertOrders(QueryOrderBy.CREATED_DATE_TIME_ASC, expectedTitles(CREATED_ASC));
    }

    @Test
    public void createdDateTimeDesc() {
        assertOrders(QueryOrderBy.CREATED_DATE_TIME_DESC, expectedTitles(CREATED_DESC));
    }

    @Test
    public void nullOrderByUsesDefaultComposite() {
        assertOrders(null, expectedTitles(DEFAULT_COMPOSITE));
    }

    /**
     * The main dataset's updated values are all distinct, so the composite's
     * created-DESC tiebreaker would never engage there. This dedicated test
     * adds checklists sharing updated values pairwise to pin "updated DESC,
     * created DESC" fully across the merged table. Single-key orders are
     * intentionally NOT asserted on the shared-updated rows (a tied single
     * key has an unspecified SQLite order).
     */
    @Test
    public void defaultCompositeEngagesCreatedTiebreaker() {
        ItemChecklist highUpdated = checklist("tie-updated-high",
                date(0), date(20));
        ItemChecklist sharedUpdatedHighCreated = checklist("tie-created-high",
                date(2), date(10));
        ItemChecklist sharedUpdatedLowCreated = checklist("tie-created-low",
                date(1), date(10));
        ItemChecklist lowUpdated = checklist("low-updated-last",
                date(3), date(0));
        // insertion order deliberately shuffled
        insertChecklist(sharedUpdatedLowCreated);
        insertChecklist(highUpdated);
        insertChecklist(lowUpdated);
        insertChecklist(sharedUpdatedHighCreated);

        // expected over the WHOLE merged table (default dataset + tie rows)
        List<ItemChecklist> allChecklists = new ArrayList<>(mInsertedChecklists);
        allChecklists.add(highUpdated);
        allChecklists.add(sharedUpdatedHighCreated);
        allChecklists.add(sharedUpdatedLowCreated);
        allChecklists.add(lowUpdated);
        allChecklists.sort(DEFAULT_COMPOSITE);
        List<String> expected = titles(allChecklists);

        assertEquals(expected, actualTitles(
                mItemChecklistDao.findItemChecklistWithLimit(allChecklists.size() + 10, null)));
        assertEquals(expected.subList(0, 3), actualTitles(
                mItemChecklistDao.findItemChecklistWithLimit(3, null)));
    }

    private static ItemChecklist checklist(String title, Date created, Date updated) {
        ItemChecklist itemChecklist = new ItemChecklist();
        itemChecklist.title = title;
        itemChecklist.createdDateTime = created;
        itemChecklist.updatedDateTime = updated;
        return itemChecklist;
    }
}
