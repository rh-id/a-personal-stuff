package m.co.rh.id.a_personal_stuff.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.observers.TestObserver;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.a_personal_stuff.item_checklist.room.ItemChecklistDatabase;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.room.ItemMaintenanceDatabase;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;
import m.co.rh.id.a_personal_stuff.item_purchase.room.ItemPurchaseDatabase;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.room.ItemReminderDatabase;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.a_personal_stuff.item_usage.room.ItemUsageDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.apoi_spreadsheet.base.POISpreadsheetContext;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Cell;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.CellType;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.DateUtil;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Row;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Sheet;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.xssf.usermodel.XSSFWorkbook;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

@RunWith(AndroidJUnit4.class)
public class ExportSpreadsheetIntegrationTest {
    private static final int EXPECTED_SHEET_COUNT = 7;
    private static final List<String> EXPECTED_SHEET_NAMES = Arrays.asList(
            "Items", "Usages", "Purchases", "Maintenances",
            "Reminders", "Checklists", "Checklist Items");

    private Context mContext;
    private AppDatabase mAppDb;
    private ItemMaintenanceDatabase mMaintenanceDb;
    private ItemUsageDatabase mUsageDb;
    private ItemReminderDatabase mReminderDb;
    private ItemPurchaseDatabase mPurchaseDb;
    private ItemChecklistDatabase mChecklistDb;
    private Provider mProvider;

    @Before
    public void setUp() {
        // POI needs java.nio.file (API 26+), same contract as the UI feature:
        // the XLSX export is runtime-guarded, so skip these tests on older devices.
        assumeTrue(ExportSpreadsheetCmd.isSupported());

        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mAppDb = Room.inMemoryDatabaseBuilder(mContext, AppDatabase.class)
                .allowMainThreadQueries().build();

        mMaintenanceDb = Room.inMemoryDatabaseBuilder(mContext, ItemMaintenanceDatabase.class)
                .allowMainThreadQueries().build();

        mUsageDb = Room.inMemoryDatabaseBuilder(mContext, ItemUsageDatabase.class)
                .allowMainThreadQueries().build();

        mReminderDb = Room.inMemoryDatabaseBuilder(mContext, ItemReminderDatabase.class)
                .allowMainThreadQueries().build();

        mPurchaseDb = Room.inMemoryDatabaseBuilder(mContext, ItemPurchaseDatabase.class)
                .allowMainThreadQueries().build();

        mChecklistDb = Room.inMemoryDatabaseBuilder(mContext, ItemChecklistDatabase.class)
                .allowMainThreadQueries().build();

        POISpreadsheetContext.getInstance().setAppContext(mContext);

        mProvider = Provider.createProvider(mContext, new TestExportSpreadsheetProviderModule());
    }

    @After
    public void tearDown() {
        if (mAppDb != null) {
            mAppDb.close();
        }
        if (mMaintenanceDb != null) {
            mMaintenanceDb.close();
        }
        if (mUsageDb != null) {
            mUsageDb.close();
        }
        if (mReminderDb != null) {
            mReminderDb.close();
        }
        if (mPurchaseDb != null) {
            mPurchaseDb.close();
        }
        if (mChecklistDb != null) {
            mChecklistDb.close();
        }
    }

    @Test
    public void exportEmptyDatabase() throws Exception {
        File xlsxFile = new ExportSpreadsheetCmd(mProvider).execute().blockingGet();
        try {
            assertNotNull(xlsxFile);
            assertTrue(xlsxFile.exists());
            assertTrue(xlsxFile.length() > 0);

            XSSFWorkbook workbook = openWorkbook(xlsxFile);
            try {
                assertEquals(EXPECTED_SHEET_COUNT, workbook.getNumberOfSheets());
                for (String sheetName : EXPECTED_SHEET_NAMES) {
                    Sheet sheet = workbook.getSheet(sheetName);
                    assertNotNull("Missing sheet: " + sheetName, sheet);
                    assertEquals("Sheet " + sheetName + " should only contain the header row",
                            1, sheet.getPhysicalNumberOfRows());
                }
            } finally {
                workbook.close();
            }
        } finally {
            xlsxFile.delete();
        }
    }

    @Test
    public void exportWithFullData() throws Exception {
        Date now = new Date();

        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Full Item";
        item.amount = 3;
        item.price = new BigDecimal("29.99");
        item.description = "Complete test item";
        item.expiredDateTime = new Date(now.getTime() + 2592000000L);
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);

        ItemTag itemTag = new ItemTag();
        itemTag.tag = "fulltest";
        itemTag.createdDateTime = now;
        ItemTag itemTag2 = new ItemTag();
        itemTag2.tag = "another";
        itemTag2.createdDateTime = now;
        // TreeSet sorts the tags, so "another" is joined before "fulltest"
        TreeSet<ItemTag> itemTags = new TreeSet<>();
        itemTags.add(itemTag);
        itemTags.add(itemTag2);
        itemState.updateItemTags(itemTags);

        // Image metadata rows only (no actual files needed); img2 gets a later
        // timestamp because the export orders item images by created_date_time.
        ItemImage img1 = new ItemImage();
        img1.fileName = "img1.jpg";
        img1.createdDateTime = now;
        ItemImage img2 = new ItemImage();
        img2.fileName = "img2.jpg";
        img2.createdDateTime = new Date(now.getTime() + 1000);
        ArrayList<ItemImage> itemImages = new ArrayList<>();
        itemImages.add(img1);
        itemImages.add(img2);
        itemState.updateItemImages(itemImages);

        mAppDb.itemDao().insertItem(itemState);
        long itemId = itemState.getItemId();

        // ItemChecklistItem has a unique index on (item_checklist_id, item_id),
        // so the checked checklist entry must reference a second item.
        ItemState itemState2 = new ItemState();
        Item item2 = new Item();
        item2.name = "Full Item 2";
        item2.amount = 1;
        // 300-char description drives the description column's auto-computed
        // width into its 100-char cap (see ExportSpreadsheetCmd width constants).
        char[] longDescriptionChars = new char[300];
        Arrays.fill(longDescriptionChars, 'x');
        String longDescription = new String(longDescriptionChars);
        item2.description = longDescription;
        item2.createdDateTime = now;
        item2.updatedDateTime = now;
        itemState2.updateItem(item2);
        mAppDb.itemDao().insertItem(itemState2);
        long itemId2 = itemState2.getItemId();

        ItemMaintenance maintenance = new ItemMaintenance();
        maintenance.itemId = itemId;
        maintenance.description = "Oil change";
        maintenance.cost = new BigDecimal("50.00");
        maintenance.maintenanceDateTime = now;
        maintenance.createdDateTime = now;
        long maintenanceId = mMaintenanceDb.itemMaintenanceDao().insert(maintenance);

        ItemMaintenanceImage maintenanceImage = new ItemMaintenanceImage();
        maintenanceImage.itemMaintenanceId = maintenanceId;
        maintenanceImage.fileName = "mimg1.jpg";
        maintenanceImage.createdDateTime = now;
        mMaintenanceDb.itemMaintenanceDao().insert(maintenanceImage);

        ItemUsage usage = new ItemUsage();
        usage.itemId = itemId;
        usage.description = "Used for project";
        usage.amount = 1;
        usage.usageDateTime = now;
        usage.createdDateTime = now;
        long usageId = mUsageDb.itemUsageDao().insert(usage);

        ItemUsageImage usageImage = new ItemUsageImage();
        usageImage.itemUsageId = usageId;
        usageImage.fileName = "uimg1.jpg";
        usageImage.createdDateTime = now;
        mUsageDb.itemUsageDao().insert(usageImage);

        ItemPurchase purchase = new ItemPurchase();
        purchase.itemId = itemId;
        purchase.description = "Bought 3 units";
        purchase.amount = 3;
        purchase.cost = new BigDecimal("12.50");
        purchase.purchaseDateTime = now;
        purchase.createdDateTime = now;
        long purchaseId = mPurchaseDb.itemPurchaseDao().insert(purchase);

        ItemPurchaseImage purchaseImage = new ItemPurchaseImage();
        purchaseImage.itemPurchaseId = purchaseId;
        purchaseImage.fileName = "pimg1.jpg";
        purchaseImage.createdDateTime = now;
        mPurchaseDb.itemPurchaseDao().insert(purchaseImage);

        ItemReminder reminder = new ItemReminder();
        reminder.itemId = itemId;
        reminder.taskId = "task-123";
        reminder.reminderDateTime = new Date(now.getTime() + 86400000);
        reminder.message = "Check item status";
        reminder.createdDateTime = now;
        mReminderDb.itemReminderDao().insertItemReminder(reminder);

        ItemChecklist checklist = new ItemChecklist();
        checklist.title = "Test Checklist";
        checklist.description = "Test description";
        checklist.createdDateTime = now;
        checklist.updatedDateTime = now;
        ItemChecklistState checklistState = new ItemChecklistState();
        checklistState.updateItemChecklist(checklist);
        mChecklistDb.itemChecklistDao().insertItemChecklist(checklistState);
        long checklistId = checklistState.getChecklistId();

        ItemChecklistItem uncheckedChecklistItem = new ItemChecklistItem();
        uncheckedChecklistItem.itemChecklistId = checklistId;
        uncheckedChecklistItem.itemId = itemId;
        uncheckedChecklistItem.createdDateTime = now;
        uncheckedChecklistItem.checkedDateTime = null;
        // insertItemChecklistItems uses OnConflictStrategy.IGNORE and reports -1
        // per dropped row: fail fast at the seed site instead of surfacing the
        // collision later as a missing export row.
        List<Long> insertedUncheckedIds = mChecklistDb.itemChecklistDao()
                .insertItemChecklistItems(Collections.singletonList(uncheckedChecklistItem));
        assertTrue("checklist item seed was silently dropped",
                insertedUncheckedIds.get(0) != -1L);

        ItemChecklistItem checkedChecklistItem = new ItemChecklistItem();
        checkedChecklistItem.itemChecklistId = checklistId;
        checkedChecklistItem.itemId = itemId2;
        checkedChecklistItem.createdDateTime = now;
        checkedChecklistItem.checkedDateTime = now;
        List<Long> insertedCheckedIds = mChecklistDb.itemChecklistDao()
                .insertItemChecklistItems(Collections.singletonList(checkedChecklistItem));
        assertTrue("checklist item seed was silently dropped",
                insertedCheckedIds.get(0) != -1L);

        File xlsxFile = new ExportSpreadsheetCmd(mProvider).execute().blockingGet();
        try {
            XSSFWorkbook workbook = openWorkbook(xlsxFile);
            try {
                assertEquals(EXPECTED_SHEET_COUNT, workbook.getNumberOfSheets());

                Sheet itemsSheet = workbook.getSheet("Items");
                assertNotNull(itemsSheet);
                assertEquals(3, itemsSheet.getPhysicalNumberOfRows());
                assertEquals(11, itemsSheet.getRow(0).getPhysicalNumberOfCells());
                // Item rows are ordered by the export's expired_date_time DESC sort
                // (NULLs last), not insertion order: "Full Item" (future expiry) is
                // row 1, tagless "Full Item 2" (null expiry) is row 2.
                Row itemRow = itemsSheet.getRow(1);
                assertNotNull(itemRow);
                // fresh in-memory DB, so the first inserted item has id 1; pin
                // the TEXT-ID writer contract on it
                assertEquals(CellType.STRING, itemRow.getCell(0).getCellType());
                assertEquals("1", itemRow.getCell(0).getStringCellValue());
                assertEquals("Full Item", itemRow.getCell(1).getStringCellValue());
                assertEquals(3, itemRow.getCell(2).getNumericCellValue(), 0);
                assertEquals(29.99, itemRow.getCell(3).getNumericCellValue(), 0.001);
                assertTrue(!isCellBlank(itemRow.getCell(6)));
                assertTrue(!isCellBlank(itemRow.getCell(7)));
                // expiredDateTime (cell 6) round-trips the Excel date serial:
                // Room stores the Date as epoch millis, the serial is a double,
                // so a 1s tolerance covers the conversion precision.
                Date expectedExpiry = item.expiredDateTime;
                Date actualExpiry = DateUtil.getJavaDate(itemRow.getCell(6).getNumericCellValue());
                assertTrue(Math.abs(actualExpiry.getTime() - expectedExpiry.getTime()) <= 1000);
                assertEquals("another, fulltest", itemRow.getCell(9).getStringCellValue());
                assertEquals("img1.jpg, img2.jpg", itemRow.getCell(10).getStringCellValue());
                assertEquals("Full Item 2", itemsSheet.getRow(2).getCell(1).getStringCellValue());
                assertTrue(isCellBlank(itemsSheet.getRow(2).getCell(9)));
                assertTrue(isCellBlank(itemsSheet.getRow(2).getCell(10)));

                // Column widths are auto-computed from content length
                // (POI width unit is 1/256 of a character width):
                // - the 300-char description caps the description column at
                //   MAX_CHARS (100) and still reads back intact
                assertEquals(longDescription,
                        itemsSheet.getRow(2).getCell(4).getStringCellValue());
                assertEquals(100 * 256, itemsSheet.getColumnWidth(4));
                // - the name column fits its longest content ("Full Item 2")
                assertTrue(itemsSheet.getColumnWidth(1) >= ("Full Item 2".length() + 2) * 256);
                // - single-char TEXT ids get the 10-char width floor
                assertTrue(itemsSheet.getColumnWidth(0)
                        >= Math.min(Math.max("1".length() + 2, 10), 100) * 256);
                // - the joined tags column fits its longest content
                assertTrue(itemsSheet.getColumnWidth(9)
                        >= ("another, fulltest".length() + 2) * 256);

                Sheet usagesSheet = workbook.getSheet("Usages");
                assertNotNull(usagesSheet);
                assertEquals(2, usagesSheet.getPhysicalNumberOfRows());
                assertEquals(8, usagesSheet.getRow(0).getPhysicalNumberOfCells());
                assertEquals("Full Item", usagesSheet.getRow(1).getCell(2).getStringCellValue());
                assertEquals("uimg1.jpg", usagesSheet.getRow(1).getCell(7).getStringCellValue());

                Sheet purchasesSheet = workbook.getSheet("Purchases");
                assertNotNull(purchasesSheet);
                assertEquals(2, purchasesSheet.getPhysicalNumberOfRows());
                assertEquals(9, purchasesSheet.getRow(0).getPhysicalNumberOfCells());
                assertEquals("Full Item", purchasesSheet.getRow(1).getCell(2).getStringCellValue());
                // cost column (id, itemId, itemName, description, amount, cost, ...)
                assertEquals(12.50,
                        purchasesSheet.getRow(1).getCell(5).getNumericCellValue(), 0.001);
                assertEquals("pimg1.jpg", purchasesSheet.getRow(1).getCell(8).getStringCellValue());

                Sheet maintenancesSheet = workbook.getSheet("Maintenances");
                assertNotNull(maintenancesSheet);
                assertEquals(2, maintenancesSheet.getPhysicalNumberOfRows());
                assertEquals(8, maintenancesSheet.getRow(0).getPhysicalNumberOfCells());
                assertEquals("Full Item", maintenancesSheet.getRow(1).getCell(2).getStringCellValue());
                assertEquals("mimg1.jpg", maintenancesSheet.getRow(1).getCell(7).getStringCellValue());

                Sheet remindersSheet = workbook.getSheet("Reminders");
                assertNotNull(remindersSheet);
                assertEquals(2, remindersSheet.getPhysicalNumberOfRows());
                assertEquals(6, remindersSheet.getRow(0).getPhysicalNumberOfCells());
                assertEquals("Full Item", remindersSheet.getRow(1).getCell(2).getStringCellValue());
                // message column (id, itemId, itemName, message, ...)
                assertEquals("Check item status",
                        remindersSheet.getRow(1).getCell(3).getStringCellValue());

                Sheet checklistsSheet = workbook.getSheet("Checklists");
                assertNotNull(checklistsSheet);
                assertEquals(2, checklistsSheet.getPhysicalNumberOfRows());
                assertEquals(5, checklistsSheet.getRow(0).getPhysicalNumberOfCells());
                assertEquals("Test Checklist", checklistsSheet.getRow(1).getCell(1).getStringCellValue());

                Sheet checklistItemsSheet = workbook.getSheet("Checklist Items");
                assertNotNull(checklistItemsSheet);
                assertEquals(3, checklistItemsSheet.getPhysicalNumberOfRows());
                assertEquals(7, checklistItemsSheet.getRow(0).getPhysicalNumberOfCells());
                Row checklistItemRow1 = checklistItemsSheet.getRow(1);
                Row checklistItemRow2 = checklistItemsSheet.getRow(2);
                assertNotNull(checklistItemRow1);
                assertNotNull(checklistItemRow2);
                // ID columns (id at 0, checklistId at 1) are TEXT cells; rows are
                // written keyset id ASC, so row 1 = id 1 (unchecked, first insert)
                // and row 2 = id 2 (checked) — see the classification loop below.
                assertEquals(CellType.STRING, checklistItemRow1.getCell(0).getCellType());
                assertEquals("1", checklistItemRow1.getCell(0).getStringCellValue());
                assertEquals(CellType.STRING, checklistItemRow1.getCell(1).getCellType());
                assertEquals(CellType.STRING, checklistItemRow2.getCell(0).getCellType());
                assertEquals("2", checklistItemRow2.getCell(0).getStringCellValue());
                assertEquals(CellType.STRING, checklistItemRow2.getCell(1).getCellType());
                // Checklist item rows are written in keyset id ASC order (the
                // unchecked row was inserted first); classification is still
                // done via checked_date_time (cell 5) as a belt-and-braces guard.
                int checkedRows = 0;
                int uncheckedRows = 0;
                for (Row row : Arrays.asList(checklistItemRow1, checklistItemRow2)) {
                    assertEquals("Test Checklist", row.getCell(2).getStringCellValue());
                    if (isCellBlank(row.getCell(5))) {
                        uncheckedRows++;
                        assertEquals("Full Item", row.getCell(4).getStringCellValue());
                    } else {
                        checkedRows++;
                        assertEquals("Full Item 2", row.getCell(4).getStringCellValue());
                    }
                }
                assertEquals(1, checkedRows);
                assertEquals(1, uncheckedRows);
            } finally {
                workbook.close();
            }
        } finally {
            xlsxFile.delete();
        }
    }

    @Test
    public void exportWithNullValues() throws Exception {
        Date now = new Date();

        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Null Values Item";
        item.amount = 1;
        item.price = null;
        item.description = null;
        item.barcode = null;
        item.expiredDateTime = null;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);

        // An image row with a null file name must be skipped so the Images
        // cell exports as blank instead of a stray separator.
        ItemImage nullImage = new ItemImage();
        nullImage.fileName = null;
        nullImage.createdDateTime = now;
        // 2^31 exceeds Integer.MAX_VALUE: the empty in-memory DB has no
        // conflict and Room honors the explicit PK, pinning the TEXT-ID
        // scientific-notation guard above the int range. The image row must
        // reference the same id (insertItem re-parents images to the inserted
        // item's id anyway).
        long largeId = 2147483648L;
        item.id = largeId;
        nullImage.itemId = largeId;
        ArrayList<ItemImage> itemImages = new ArrayList<>();
        itemImages.add(nullImage);
        itemState.updateItemImages(itemImages);

        mAppDb.itemDao().insertItem(itemState);

        File xlsxFile = new ExportSpreadsheetCmd(mProvider).execute().blockingGet();
        try {
            XSSFWorkbook workbook = openWorkbook(xlsxFile);
            try {
                Sheet itemsSheet = workbook.getSheet("Items");
                assertNotNull(itemsSheet);
                assertEquals(2, itemsSheet.getPhysicalNumberOfRows());
                Row itemRow = itemsSheet.getRow(1);
                assertNotNull(itemRow);
                // the large id exports as TEXT, never scientific notation
                assertEquals(CellType.STRING, itemRow.getCell(0).getCellType());
                assertEquals("2147483648", itemRow.getCell(0).getStringCellValue());
                assertEquals("Null Values Item", itemRow.getCell(1).getStringCellValue());
                assertTrue(isCellBlank(itemRow.getCell(3)));
                assertTrue(isCellBlank(itemRow.getCell(4)));
                assertTrue(isCellBlank(itemRow.getCell(5)));
                assertTrue(isCellBlank(itemRow.getCell(6)));
                assertTrue(isCellBlank(itemRow.getCell(10)));
            } finally {
                workbook.close();
            }
        } finally {
            xlsxFile.delete();
        }
    }

    @Test
    public void exportWithLargeUsageCount() throws Exception {
        Date now = new Date();

        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Bulk Item";
        item.amount = 1;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);
        mAppDb.itemDao().insertItem(itemState);
        long itemId = itemState.getItemId();

        for (int i = 1; i <= 150; i++) {
            ItemUsage usage = new ItemUsage();
            usage.itemId = itemId;
            usage.description = "Usage " + i;
            usage.amount = 1;
            usage.usageDateTime = now;
            usage.createdDateTime = now;
            mUsageDb.itemUsageDao().insert(usage);
        }

        File xlsxFile = new ExportSpreadsheetCmd(mProvider).execute().blockingGet();
        try {
            XSSFWorkbook workbook = openWorkbook(xlsxFile);
            try {
                Sheet usagesSheet = workbook.getSheet("Usages");
                assertNotNull(usagesSheet);
                // 150 data rows + header; exercises the SXSSF 100-row window flush
                assertEquals(151, usagesSheet.getPhysicalNumberOfRows());
                // Usages column order: id, itemId, itemName, description, ...
                // Rows are written in keyset id ASC order; the order-insensitive
                // set assertion guards against duplication or loss within the
                // single-page write (page boundaries are covered by
                // exportWithMultiPageUsages).
                Set<String> descriptions = new HashSet<>();
                for (int i = 1; i <= 150; i++) {
                    descriptions.add(usagesSheet.getRow(i).getCell(3).getStringCellValue());
                }
                assertEquals(150, descriptions.size());
                assertTrue(descriptions.contains("Usage 1"));
                assertTrue(descriptions.contains("Usage 150"));
                // no usage images were seeded, so the Images column stays blank
                assertTrue(isCellBlank(usagesSheet.getRow(1).getCell(7)));
                // the description column fits the longest content ("Usage 150");
                // the all-blank Images column falls back to the 10-char width
                // floor unless a localized header is longer (derived from the
                // resource so the assertion tracks the device locale)
                assertTrue(usagesSheet.getColumnWidth(3) >= ("Usage 150".length() + 2) * 256);
                int imagesHeaderChars = mContext.getString(R.string.header_images).length();
                assertEquals(Math.min(Math.max(imagesHeaderChars + 2, 10), 100) * 256,
                        usagesSheet.getColumnWidth(7));
            } finally {
                workbook.close();
            }
        } finally {
            xlsxFile.delete();
        }
    }

    @Test
    public void exportWithMultiPageUsages() throws Exception {
        Date now = new Date();

        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Bulk Item";
        item.amount = 1;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);
        mAppDb.itemDao().insertItem(itemState);
        long itemId = itemState.getItemId();

        // 1200 rows = 500 + 500 + 200 keyset pages: proves pages are stitched
        // together with no row lost or duplicated at the page boundaries.
        seedUsages(itemId, 1200);

        File xlsxFile = new ExportSpreadsheetCmd(mProvider).execute().blockingGet();
        try {
            XSSFWorkbook workbook = openWorkbook(xlsxFile);
            try {
                Sheet usagesSheet = workbook.getSheet("Usages");
                assertNotNull(usagesSheet);
                // 1200 data rows + header across 3 keyset pages
                assertEquals(1201, usagesSheet.getPhysicalNumberOfRows());
                // Rows are written in keyset id ASC order (500/500/200); the
                // order-insensitive set assertion guards against row loss or
                // duplication at the page boundaries.
                Set<String> descriptions = new HashSet<>();
                for (int i = 1; i <= 1200; i++) {
                    descriptions.add(usagesSheet.getRow(i).getCell(3).getStringCellValue());
                }
                assertEquals(1200, descriptions.size());
                assertTrue(descriptions.contains("Usage 1"));
                assertTrue(descriptions.contains("Usage 1200"));
                // no usage images were seeded, so the Images column stays blank
                assertTrue(isCellBlank(usagesSheet.getRow(1).getCell(7)));
            } finally {
                workbook.close();
            }
        } finally {
            xlsxFile.delete();
        }
    }

    @Test
    public void exportProgressFlowAndLastProgress() throws Exception {
        Date now = new Date();

        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Progress Item";
        item.amount = 1;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);
        mAppDb.itemDao().insertItem(itemState);

        List<String> progress = Collections.synchronizedList(new ArrayList<>());
        ExportSpreadsheetCmd cmd = new ExportSpreadsheetCmd(mProvider);
        // Subscribe BEFORE execute(): PublishSubject delivers synchronously to
        // observers on the emitting (POI) thread, and blockingGet's FutureTask
        // completion provides the happens-before edge, so the list is fully
        // populated and visible once blockingGet returns.
        cmd.getProgressFlow().subscribe(progress::add);

        File xlsxFile = cmd.execute().blockingGet();
        try {
            String gathering = mContext.getString(R.string.export_progress_gathering);
            String writing = mContext.getString(R.string.export_spreadsheet_progress_writing);
            // gathering + 7 sheet messages + writing
            assertEquals(9, progress.size());
            assertEquals(gathering, progress.get(0));
            for (int i = 1; i <= 7; i++) {
                assertEquals(String.format(
                        mContext.getString(R.string.export_spreadsheet_progress_sheet), i, 7),
                        progress.get(i));
            }
            assertEquals(writing, progress.get(8));
            // clearInFlight() runs on the POI thread after the terminal event is
            // delivered, so wait for the in-flight state to clear before asserting
            // (isExporting() shares its lock with the cleanup, giving the
            // happens-before edge). Termination drops the stale progress message.
            long deadline = System.currentTimeMillis() + 10_000;
            while (cmd.isExporting() && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertFalse(cmd.isExporting());
            assertNull(cmd.getLastProgress());
        } finally {
            xlsxFile.delete();
        }
    }

    @Test
    public void exportInFlightReusesSameResult() throws Exception {
        Date now = new Date();

        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Bulk Item";
        item.amount = 1;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);
        mAppDb.itemDao().insertItem(itemState);
        long itemId = itemState.getItemId();

        // same 1,200-usage seed as exportWithMultiPageUsages: the export takes
        // ~1.4s, making the in-flight window deterministically observable (the
        // re-subscribe window after execute() is only microseconds long).
        seedUsages(itemId, 1200);

        ExportSpreadsheetCmd cmd = new ExportSpreadsheetCmd(mProvider);
        TestObserver<File> first = null;
        TestObserver<File> second = null;
        try {
            first = cmd.execute().test();
            assertTrue("first execute() should own the in-flight export", cmd.isExporting());
            // re-attach while the first export is still running
            second = cmd.execute().test();
            assertTrue("re-attach must not cancel the running export", cmd.isExporting());
            assertTrue(first.await(30, TimeUnit.SECONDS));
            assertTrue(second.await(30, TimeUnit.SECONDS));
            first.assertNoErrors();
            second.assertNoErrors();
            // same File instance — no second run of the export
            assertSame(first.values().get(0), second.values().get(0));
            assertFalse(cmd.isExporting());
        } finally {
            // cleanup must not depend on the assertions above passing: delete
            // whatever the observers captured, so a failed await cannot leak
            // the exported temp file
            deleteExportedFiles(first, second);
        }
    }

    /**
     * Deletes any exported file captured by the given observers (best effort)
     * so cleanup is independent of the test assertions having succeeded — both
     * observers normally share the same {@link File} instance and
     * {@link File#delete()} is idempotent.
     */
    private static void deleteExportedFiles(TestObserver<File> first,
                                            TestObserver<File> second) {
        for (TestObserver<File> observer : Arrays.asList(first, second)) {
            if (observer != null) {
                for (File file : observer.values()) {
                    file.delete();
                }
            }
        }
    }

    /**
     * Seeds {@code count} usages for one item, each described "Usage i" (unique
     * per i — the multi-page test's set assertions rely on that), and returns
     * the seeded rows in insertion order.
     */
    private List<ItemUsage> seedUsages(long itemId, int count) {
        Date now = new Date();
        List<ItemUsage> usages = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            ItemUsage usage = new ItemUsage();
            usage.itemId = itemId;
            usage.description = "Usage " + i;
            usage.amount = 1;
            usage.usageDateTime = now;
            usage.createdDateTime = now;
            mUsageDb.itemUsageDao().insert(usage);
            usages.add(usage);
        }
        return usages;
    }

    private XSSFWorkbook openWorkbook(File xlsxFile) throws Exception {
        FileInputStream fis = new FileInputStream(xlsxFile);
        try {
            return new XSSFWorkbook(fis);
        } finally {
            fis.close();
        }
    }

    private boolean isCellBlank(Cell cell) {
        if (cell == null) {
            return true;
        }
        CellType cellType = cell.getCellType();
        if (cellType == CellType.BLANK) {
            return true;
        }
        if (cellType == CellType.STRING) {
            return cell.getStringCellValue().isEmpty();
        }
        return false;
    }

    private class TestExportSpreadsheetProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ILogger.class, this::getLogger);
            providerRegistry.register(ItemDao.class, this::getItemDao);
            providerRegistry.register(ItemMaintenanceDao.class, this::getItemMaintenanceDao);
            providerRegistry.register(ItemUsageDao.class, this::getItemUsageDao);
            providerRegistry.register(ItemReminderDao.class, this::getItemReminderDao);
            providerRegistry.register(ItemPurchaseDao.class, this::getItemPurchaseDao);
            providerRegistry.register(ItemChecklistDao.class, this::getItemChecklistDao);
            providerRegistry.registerLazy(FileHelper.class, () -> new FileHelper(provider));
        }

        private ILogger getLogger() {
            return new TestLogger();
        }

        private ItemDao getItemDao() {
            return mAppDb.itemDao();
        }

        private ItemMaintenanceDao getItemMaintenanceDao() {
            return mMaintenanceDb.itemMaintenanceDao();
        }

        private ItemUsageDao getItemUsageDao() {
            return mUsageDb.itemUsageDao();
        }

        private ItemReminderDao getItemReminderDao() {
            return mReminderDb.itemReminderDao();
        }

        private ItemPurchaseDao getItemPurchaseDao() {
            return mPurchaseDb.itemPurchaseDao();
        }

        private ItemChecklistDao getItemChecklistDao() {
            return mChecklistDb.itemChecklistDao();
        }
    }

    private static class TestLogger implements ILogger {
        @Override
        public void v(String tag, String message) {
        }

        @Override
        public void v(String tag, String message, Throwable throwable) {
        }

        @Override
        public void d(String tag, String message) {
        }

        @Override
        public void d(String tag, String message, Throwable throwable) {
        }

        @Override
        public void i(String tag, String message) {
        }

        @Override
        public void i(String tag, String message, Throwable throwable) {
        }

        @Override
        public void w(String tag, String message) {
        }

        @Override
        public void w(String tag, String message, Throwable throwable) {
        }

        @Override
        public void e(String tag, String message) {
        }

        @Override
        public void e(String tag, String message, Throwable throwable) {
        }

        @Override
        public void setLogLevel(int logLevel) {
        }
    }
}
