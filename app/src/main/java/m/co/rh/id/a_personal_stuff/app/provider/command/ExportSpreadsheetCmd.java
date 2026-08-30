package m.co.rh.id.a_personal_stuff.app.provider.command;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.item_checklist.dao.ItemChecklistDao;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.apoi_spreadsheet.base.POISpreadsheetContext;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Cell;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.CellStyle;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Font;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Row;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Sheet;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.xssf.streaming.SXSSFWorkbook;
import m.co.rh.id.aprovider.Provider;

/**
 * Command to export all app data as a multi-sheet XLSX spreadsheet file.
 * Requires API 26 or higher (see {@link #isSupported()}), the POI spreadsheet
 * library does not support lower API levels.
 * <p>
 * Memory strategy: Items and Checklists are deliberately full-loaded because
 * they are small anchor tables whose rows also feed the item-name and
 * checklist-title join maps of the other sheets. The high-volume tables
 * (item_usage, item_purchase, item_maintenance, item_reminder,
 * item_checklist_item and the three image tables) are keyset-paged
 * ({@code WHERE id > :lastId ORDER BY id ASC LIMIT :limit}, {@link #PAGE_SIZE}
 * rows per page): a page is loaded, written to the sheet, then dropped, which
 * bounds peak memory regardless of table size.
 * <p>
 * In-flight/result model: {@link #execute()} kicks off the export work once
 * and returns a cached shared {@link Single} that every caller (including
 * late re-attaching views) subscribes to — see the {@link #execute()} javadoc
 * for the full mechanics and the known trade-off when the export terminates
 * while no view is subscribed.
 */
public class ExportSpreadsheetCmd {
    private static final String SHEET_ITEMS = "Items";
    private static final String SHEET_USAGES = "Usages";
    private static final String SHEET_PURCHASES = "Purchases";
    private static final String SHEET_MAINTENANCES = "Maintenances";
    private static final String SHEET_REMINDERS = "Reminders";
    private static final String SHEET_CHECKLISTS = "Checklists";
    private static final String SHEET_CHECKLIST_ITEMS = "Checklist Items";
    private static final String DATE_FORMAT = "yyyyMMdd_HHmmss";
    private static final String XLSX_EXTENSION = ".xlsx";
    private static final String TAG_SEPARATOR = ", ";
    private static final int TOTAL_SHEETS = 7;
    /**
     * Keyset page size. MUST stay below ~900: the per-page image lookup uses
     * {@code IN (:ids)} with one bind parameter per id, and SQLite on the
     * minimum supported API (API 26 ships SQLite 3.18) caps a statement at
     * 999 variables (SQLITE_MAX_VARIABLE_NUMBER). If a larger page is ever
     * needed, chunk the IN lists into batches of &lt;= 900 ids and merge.
     */
    private static final int PAGE_SIZE = 500;
    /**
     * Column widths are computed manually from content length instead of
     * {@code Sheet.autoSizeColumn(int)}: autoSizeColumn measures text via
     * java.awt font metrics (unavailable on this Android POI port) and on the
     * SXSSF streaming writer it requires the column's rows to be tracked in
     * memory. Instead each sheet writer records the longest display length
     * per column (headers included) and the widths are applied once after
     * all rows are written (see {@link #applyColumnWidths(Sheet, int[])}).
     */
    private static final int COLUMN_WIDTH_PADDING_CHARS = 2;
    /** Width floor so narrow ID/date columns stay readable. */
    private static final int COLUMN_WIDTH_MIN_CHARS = 10;
    /** Width cap so runaway description/tag/image columns stay navigable. */
    private static final int COLUMN_WIDTH_MAX_CHARS = 100;
    /** POI column width unit: 1/256 of a character width. */
    private static final int COLUMN_WIDTH_CHAR_UNITS = 256;
    /** Display length of a cell styled with the "yyyy-mm-dd HH:mm:ss" format. */
    private static final int DATE_CELL_DISPLAY_CHARS = "yyyy-mm-dd HH:mm:ss".length();

    private final Context mAppContext;
    private final FileHelper mFileHelper;
    private final Subject<String> mProgressSubject = PublishSubject.create();
    /** POI writes the workbook on its dedicated single-thread executor. */
    private final Scheduler mSpreadsheetScheduler =
            Schedulers.from(POISpreadsheetContext.getInstance());
    private final Object mExecuteLock = new Object();
    private final ItemDao mItemDao;
    private final ItemMaintenanceDao mItemMaintenanceDao;
    private final ItemUsageDao mItemUsageDao;
    private final ItemPurchaseDao mItemPurchaseDao;
    private final ItemReminderDao mItemReminderDao;
    private final ItemChecklistDao mItemChecklistDao;
    private volatile String mLastProgress;
    private Disposable mWorkDisposable;
    private Single<File> mSharedResult;

    public ExportSpreadsheetCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mFileHelper = provider.get(FileHelper.class);
        mItemDao = provider.get(ItemDao.class);
        mItemMaintenanceDao = provider.get(ItemMaintenanceDao.class);
        mItemUsageDao = provider.get(ItemUsageDao.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
        mItemReminderDao = provider.get(ItemReminderDao.class);
        mItemChecklistDao = provider.get(ItemChecklistDao.class);
    }

    public Flowable<String> getProgressFlow() {
        return Flowable.fromObservable(mProgressSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * @return the last progress message emitted by the current export, or null
     * when no export is in flight (the message is cleared together with the
     * in-flight state once the export terminates).
     */
    public String getLastProgress() {
        return mLastProgress;
    }

    /**
     * @return true when this device can run the XLSX export (API 26+).
     */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    /**
     * @return true while an export started by {@link #execute()} is still running.
     */
    public boolean isExporting() {
        synchronized (mExecuteLock) {
            return mSharedResult != null;
        }
    }

    /**
     * Starts the XLSX export if none is running and returns a cached shared
     * result {@link Single}. The export work is owned by this command and runs
     * exactly once per execute cycle regardless of view lifecycle: a disposed
     * view subscription does NOT cancel the running export. Re-attached
     * subscribers (e.g. a re-created view after a configuration change)
     * subscribe to the SAME cached {@link Single} and receive the SAME result
     * without re-running the export — the cache replays the outcome to late
     * subscribers, so a subscriber that attaches between termination and
     * cleanup still gets the result instead of hanging or re-executing. The
     * in-flight state is cleared only when the work actually terminates
     * (success or error). Known trade-off: if the export terminates while no
     * view is subscribed (e.g. activity destroyed mid-export), the cached
     * result is dropped on cleanup and the user must tap export again;
     * re-attach during the run still receives the live result.
     *
     * @return {@link Single} emitting the exported XLSX {@link File}.
     */
    public Single<File> execute() {
        synchronized (mExecuteLock) {
            if (mSharedResult == null) {
                // doFinally must stay UPSTREAM of cache(): the cleanup runs once
                // per export run, not once per subscriber.
                mSharedResult = Single.fromCallable(this::exportSpreadsheet)
                        .subscribeOn(mSpreadsheetScheduler)
                        .doFinally(this::clearInFlight)
                        .cache();
                // Own the work so it outlives any view subscription. Errors are
                // deliberately ignored here: the cached Single delivers them to
                // every real subscriber, and an empty error consumer keeps this
                // ownership subscription from crashing via the global handler.
                mWorkDisposable = mSharedResult.subscribe(
                        value -> { }, throwable -> { });
            }
            return mSharedResult;
        }
    }

    /**
     * Runs once per export run (upstream of the result cache) after the work
     * terminates, success or error: clears the in-flight state so a fresh
     * {@link #execute()} can start a new export, and drops the stale progress
     * message so no re-attached view can restore it.
     */
    private void clearInFlight() {
        synchronized (mExecuteLock) {
            mWorkDisposable = null;
            mSharedResult = null;
            mLastProgress = null;
        }
    }

    private File exportSpreadsheet() throws Exception {
        if (!isSupported()) {
            throw new IllegalStateException("XLSX export requires Android 8.0 or newer");
        }
        emitProgress(mAppContext.getString(R.string.export_progress_gathering));
        // Items and Checklists are small anchor tables, deliberately full-loaded:
        // their rows feed the Items/Checklists sheets AND the item-name /
        // checklist-title join maps used by the other sheets.
        List<ItemState> itemStates = mItemDao.findItemStateWithLimit(Integer.MAX_VALUE, null);
        Map<Long, Item> itemMap = new HashMap<>();
        Map<Long, String> itemTagsMap = new HashMap<>();
        Map<Long, String> itemImagesByItemId = new HashMap<>();
        for (ItemState itemState : itemStates) {
            Item item = itemState.getItem();
            itemMap.put(item.id, item);
            itemTagsMap.put(item.id, joinTags(itemState.getItemTags()));
            for (ItemImage itemImage : itemState.getItemImages()) {
                addImageFileName(itemImagesByItemId, item.id, itemImage.fileName);
            }
        }
        List<ItemChecklist> checklists = mItemChecklistDao.findAllItemChecklists();
        Map<Long, String> checklistTitleMap = new HashMap<>();
        for (ItemChecklist checklist : checklists) {
            checklistTitleMap.put(checklist.id, checklist.title);
        }

        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        File xlsxFile;
        try {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            writeItemsSheet(workbook, headerStyle, dateStyle, itemStates, itemTagsMap,
                    itemImagesByItemId, 1);
            writeUsagesSheet(workbook, headerStyle, dateStyle, itemMap, 2);
            writePurchasesSheet(workbook, headerStyle, dateStyle, itemMap, 3);
            writeMaintenancesSheet(workbook, headerStyle, dateStyle, itemMap, 4);
            writeRemindersSheet(workbook, headerStyle, dateStyle, itemMap, 5);
            writeChecklistsSheet(workbook, headerStyle, dateStyle, checklists, 6);
            writeChecklistItemsSheet(workbook, headerStyle, dateStyle,
                    checklistTitleMap, itemMap, 7);

            emitProgress(mAppContext.getString(R.string.export_spreadsheet_progress_writing));
            String fileName = "personal_stuff_export_"
                    + new SimpleDateFormat(DATE_FORMAT, Locale.US).format(new Date())
                    + XLSX_EXTENSION;
            xlsxFile = mFileHelper.createTempFile(fileName);
            try {
                try (FileOutputStream fos = new FileOutputStream(xlsxFile)) {
                    workbook.write(fos);
                }
            } catch (Exception e) {
                // delete the partially written file so no broken .xlsx is left behind
                xlsxFile.delete();
                throw e;
            }
        } finally {
            workbook.dispose();
            workbook.close();
        }
        return xlsxFile;
    }

    private String joinTags(TreeSet<ItemTag> itemTags) {
        if (itemTags == null || itemTags.isEmpty()) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (ItemTag itemTag : itemTags) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(TAG_SEPARATOR);
            }
            stringBuilder.append(itemTag.tag);
        }
        return stringBuilder.toString();
    }

    /**
     * Appends an image file name to the joined names of its parent record
     * (item/usage/purchase/maintenance). Null or empty file names are skipped,
     * and no entry is created for parents without any valid name so their
     * export cell stays blank.
     */
    private void addImageFileName(Map<Long, String> imagesByParentId, Long parentId, String fileName) {
        if (parentId == null || fileName == null || fileName.isEmpty()) {
            return;
        }
        String joined = imagesByParentId.get(parentId);
        if (joined == null || joined.isEmpty()) {
            imagesByParentId.put(parentId, fileName);
        } else {
            imagesByParentId.put(parentId, joined + TAG_SEPARATOR + fileName);
        }
    }

    private CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFont(font);
        return cellStyle;
    }

    private CellStyle createDateStyle(SXSSFWorkbook workbook) {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd HH:mm:ss"));
        return cellStyle;
    }

    private String resolveItemName(Map<Long, Item> itemMap, Long itemId) {
        if (itemId == null) {
            return "";
        }
        Item item = itemMap.get(itemId);
        if (item == null || item.name == null) {
            return "";
        }
        return item.name;
    }

    private void emitSheetProgress(int sheetIndex) {
        emitProgress(mAppContext.getString(
                R.string.export_spreadsheet_progress_sheet, sheetIndex, TOTAL_SHEETS));
    }

    /**
     * Central progress emission: records the message for re-attach replay
     * (see {@link #getLastProgress()}) then publishes it to current subscribers.
     */
    private void emitProgress(String message) {
        mLastProgress = message;
        mProgressSubject.onNext(message);
    }

    /**
     * Writes the header row and returns one display-length entry per column
     * (seeded with each header's length) so the caller can track the widest
     * content per column for the auto-computed column widths.
     */
    private int[] writeHeaderRow(Sheet sheet, CellStyle headerStyle, String[] headers) {
        Row headerRow = sheet.createRow(0);
        int[] columnChars = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            columnChars[i] = headers[i] == null ? 0 : headers[i].length();
        }
        return columnChars;
    }

    /**
     * Writes ID columns (id/itemId/itemChecklistId) as TEXT cells so the values
     * export losslessly and never render in scientific notation.
     *
     * @return the display length of the written value, 0 when null.
     */
    private int writeLongCell(Row row, int column, Long value) {
        if (value == null) {
            return 0;
        }
        String text = String.valueOf(value);
        row.createCell(column).setCellValue(text);
        return text.length();
    }

    /**
     * @return the display length of the written value, 0 when null.
     */
    private int writeStringCell(Row row, int column, String value) {
        if (value == null) {
            return 0;
        }
        row.createCell(column).setCellValue(value);
        return maxLineLength(value);
    }

    /**
     * Writes an integer amount cell, keeping the cell a true numeric cell, and
     * tracks the column width: the display length of the plain string form is
     * returned so amount columns are measured like every other helper instead
     * of falling back to the {@link #COLUMN_WIDTH_MIN_CHARS} floor.
     *
     * @return the display length of the written value.
     */
    private int writeAmountCell(Row row, int column, int value) {
        row.createCell(column).setCellValue(value);
        return String.valueOf(value).length();
    }

    /**
     * @return the approximate display length of the written value, 0 when null.
     */
    private int writeDecimalCell(Row row, int column, BigDecimal value) {
        if (value == null) {
            return 0;
        }
        row.createCell(column).setCellValue(value.doubleValue());
        return String.valueOf(value.doubleValue()).length();
    }

    /**
     * @return the display length of the rendered date
     * ("yyyy-mm-dd HH:mm:ss"), 0 when null.
     */
    private int writeDateCell(Row row, int column, Date value, CellStyle dateStyle) {
        if (value == null) {
            return 0;
        }
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(dateStyle);
        return DATE_CELL_DISPLAY_CHARS;
    }

    /**
     * @return the length of the longest line in {@code value}; multi-line
     * content (e.g. descriptions containing newlines) only needs to fit its
     * longest line.
     */
    private static int maxLineLength(String value) {
        int maxLength = 0;
        int lineStart = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                maxLength = Math.max(maxLength, i - lineStart);
                lineStart = i + 1;
            }
        }
        return Math.max(maxLength, value.length() - lineStart);
    }

    /**
     * Applies the auto-computed column widths: each column is sized to its
     * longest display length plus padding, clamped between a readable floor
     * and a navigable cap (safely inside POI's 255-character column limit).
     */
    private static void applyColumnWidths(Sheet sheet, int[] columnChars) {
        for (int i = 0; i < columnChars.length; i++) {
            int chars = Math.min(Math.max(columnChars[i] + COLUMN_WIDTH_PADDING_CHARS,
                    COLUMN_WIDTH_MIN_CHARS), COLUMN_WIDTH_MAX_CHARS);
            sheet.setColumnWidth(i, chars * COLUMN_WIDTH_CHAR_UNITS);
        }
    }

    private void writeItemsSheet(SXSSFWorkbook workbook, CellStyle headerStyle,
                                 CellStyle dateStyle, List<ItemState> itemStates,
                                 Map<Long, String> itemTagsMap,
                                 Map<Long, String> itemImagesByItemId, int sheetIndex) {
        emitSheetProgress(sheetIndex);
        Sheet sheet = workbook.createSheet(SHEET_ITEMS);
        int[] columnChars = writeHeaderRow(sheet, headerStyle, new String[]{
                mAppContext.getString(R.string.header_id),
                mAppContext.getString(R.string.form_name),
                mAppContext.getString(R.string.form_amount),
                mAppContext.getString(R.string.form_price),
                mAppContext.getString(R.string.form_description),
                mAppContext.getString(R.string.form_barcode),
                mAppContext.getString(R.string.form_expired_date_time),
                mAppContext.getString(R.string.header_created_date_time),
                mAppContext.getString(R.string.header_updated_date_time),
                mAppContext.getString(R.string.header_tags),
                mAppContext.getString(R.string.header_images),
        });
        int rowIndex = 1;
        for (ItemState itemState : itemStates) {
            Item item = itemState.getItem();
            Row row = sheet.createRow(rowIndex++);
            columnChars[0] = Math.max(columnChars[0], writeLongCell(row, 0, item.id));
            columnChars[1] = Math.max(columnChars[1], writeStringCell(row, 1, item.name));
            columnChars[2] = Math.max(columnChars[2], writeAmountCell(row, 2, item.amount));
            columnChars[3] = Math.max(columnChars[3], writeDecimalCell(row, 3, item.price));
            columnChars[4] = Math.max(columnChars[4], writeStringCell(row, 4, item.description));
            columnChars[5] = Math.max(columnChars[5], writeStringCell(row, 5, item.barcode));
            columnChars[6] = Math.max(columnChars[6],
                    writeDateCell(row, 6, item.expiredDateTime, dateStyle));
            columnChars[7] = Math.max(columnChars[7],
                    writeDateCell(row, 7, item.createdDateTime, dateStyle));
            columnChars[8] = Math.max(columnChars[8],
                    writeDateCell(row, 8, item.updatedDateTime, dateStyle));
            columnChars[9] = Math.max(columnChars[9],
                    writeStringCell(row, 9, itemTagsMap.get(item.id)));
            columnChars[10] = Math.max(columnChars[10],
                    writeStringCell(row, 10, itemImagesByItemId.get(item.id)));
        }
        applyColumnWidths(sheet, columnChars);
    }

    /**
     * Usages are keyset-paged: each iteration loads one page ordered by id,
     * fetches the page's images in a single query, writes its rows, then
     * advances {@code lastId} so the page can be garbage collected.
     */
    private void writeUsagesSheet(SXSSFWorkbook workbook, CellStyle headerStyle,
                                  CellStyle dateStyle, Map<Long, Item> itemMap, int sheetIndex) {
        emitSheetProgress(sheetIndex);
        Sheet sheet = workbook.createSheet(SHEET_USAGES);
        int[] columnChars = writeHeaderRow(sheet, headerStyle, new String[]{
                mAppContext.getString(R.string.header_id),
                mAppContext.getString(R.string.header_item_id),
                mAppContext.getString(R.string.header_item_name),
                mAppContext.getString(R.string.form_description),
                mAppContext.getString(R.string.form_amount),
                mAppContext.getString(R.string.header_usage_date_time),
                mAppContext.getString(R.string.header_created_date_time),
                mAppContext.getString(R.string.header_images),
        });
        int rowIndex = 1;
        long lastId = 0L;
        List<ItemUsage> page;
        while (!(page = mItemUsageDao.findItemUsagesAfter(lastId, PAGE_SIZE)).isEmpty()) {
            List<Long> pageIds = page.stream().map(usage -> usage.id)
                    .collect(Collectors.toList());
            Map<Long, String> imagesByUsageId = new HashMap<>();
            for (ItemUsageImage image : mItemUsageDao.findItemUsageImagesByItemUsageIds(pageIds)) {
                addImageFileName(imagesByUsageId, image.itemUsageId, image.fileName);
            }
            for (ItemUsage usage : page) {
                Row row = sheet.createRow(rowIndex++);
                columnChars[0] = Math.max(columnChars[0], writeLongCell(row, 0, usage.id));
                columnChars[1] = Math.max(columnChars[1], writeLongCell(row, 1, usage.itemId));
                columnChars[2] = Math.max(columnChars[2],
                        writeStringCell(row, 2, resolveItemName(itemMap, usage.itemId)));
                columnChars[3] = Math.max(columnChars[3],
                        writeStringCell(row, 3, usage.description));
                columnChars[4] = Math.max(columnChars[4],
                        writeAmountCell(row, 4, usage.amount));
                columnChars[5] = Math.max(columnChars[5],
                        writeDateCell(row, 5, usage.usageDateTime, dateStyle));
                columnChars[6] = Math.max(columnChars[6],
                        writeDateCell(row, 6, usage.createdDateTime, dateStyle));
                columnChars[7] = Math.max(columnChars[7],
                        writeStringCell(row, 7, imagesByUsageId.get(usage.id)));
            }
            lastId = page.get(page.size() - 1).id;
        }
        applyColumnWidths(sheet, columnChars);
    }

    /**
     * Purchases are keyset-paged like {@link #writeUsagesSheet} (see its javadoc).
     */
    private void writePurchasesSheet(SXSSFWorkbook workbook, CellStyle headerStyle,
                                     CellStyle dateStyle, Map<Long, Item> itemMap, int sheetIndex) {
        emitSheetProgress(sheetIndex);
        Sheet sheet = workbook.createSheet(SHEET_PURCHASES);
        int[] columnChars = writeHeaderRow(sheet, headerStyle, new String[]{
                mAppContext.getString(R.string.header_id),
                mAppContext.getString(R.string.header_item_id),
                mAppContext.getString(R.string.header_item_name),
                mAppContext.getString(R.string.form_description),
                mAppContext.getString(R.string.form_amount),
                mAppContext.getString(R.string.header_cost),
                mAppContext.getString(R.string.header_purchase_date_time),
                mAppContext.getString(R.string.header_created_date_time),
                mAppContext.getString(R.string.header_images),
        });
        int rowIndex = 1;
        long lastId = 0L;
        List<ItemPurchase> page;
        while (!(page = mItemPurchaseDao.findItemPurchasesAfter(lastId, PAGE_SIZE)).isEmpty()) {
            List<Long> pageIds = page.stream().map(purchase -> purchase.id)
                    .collect(Collectors.toList());
            Map<Long, String> imagesByPurchaseId = new HashMap<>();
            for (ItemPurchaseImage image : mItemPurchaseDao.findItemPurchaseImagesByItemPurchaseIds(pageIds)) {
                addImageFileName(imagesByPurchaseId, image.itemPurchaseId, image.fileName);
            }
            for (ItemPurchase purchase : page) {
                Row row = sheet.createRow(rowIndex++);
                columnChars[0] = Math.max(columnChars[0], writeLongCell(row, 0, purchase.id));
                columnChars[1] = Math.max(columnChars[1], writeLongCell(row, 1, purchase.itemId));
                columnChars[2] = Math.max(columnChars[2],
                        writeStringCell(row, 2, resolveItemName(itemMap, purchase.itemId)));
                columnChars[3] = Math.max(columnChars[3],
                        writeStringCell(row, 3, purchase.description));
                columnChars[4] = Math.max(columnChars[4],
                        writeAmountCell(row, 4, purchase.amount));
                columnChars[5] = Math.max(columnChars[5], writeDecimalCell(row, 5, purchase.cost));
                columnChars[6] = Math.max(columnChars[6],
                        writeDateCell(row, 6, purchase.purchaseDateTime, dateStyle));
                columnChars[7] = Math.max(columnChars[7],
                        writeDateCell(row, 7, purchase.createdDateTime, dateStyle));
                columnChars[8] = Math.max(columnChars[8],
                        writeStringCell(row, 8, imagesByPurchaseId.get(purchase.id)));
            }
            lastId = page.get(page.size() - 1).id;
        }
        applyColumnWidths(sheet, columnChars);
    }

    /**
     * Maintenances are keyset-paged like {@link #writeUsagesSheet} (see its javadoc).
     */
    private void writeMaintenancesSheet(SXSSFWorkbook workbook, CellStyle headerStyle,
                                        CellStyle dateStyle, Map<Long, Item> itemMap, int sheetIndex) {
        emitSheetProgress(sheetIndex);
        Sheet sheet = workbook.createSheet(SHEET_MAINTENANCES);
        int[] columnChars = writeHeaderRow(sheet, headerStyle, new String[]{
                mAppContext.getString(R.string.header_id),
                mAppContext.getString(R.string.header_item_id),
                mAppContext.getString(R.string.header_item_name),
                mAppContext.getString(R.string.form_description),
                mAppContext.getString(R.string.header_cost),
                mAppContext.getString(R.string.header_maintenance_date_time),
                mAppContext.getString(R.string.header_created_date_time),
                mAppContext.getString(R.string.header_images),
        });
        int rowIndex = 1;
        long lastId = 0L;
        List<ItemMaintenance> page;
        while (!(page = mItemMaintenanceDao.findItemMaintenancesAfter(lastId, PAGE_SIZE)).isEmpty()) {
            List<Long> pageIds = page.stream().map(maintenance -> maintenance.id)
                    .collect(Collectors.toList());
            Map<Long, String> imagesByMaintenanceId = new HashMap<>();
            for (ItemMaintenanceImage image : mItemMaintenanceDao.findItemMaintenanceImagesByItemMaintenanceIds(pageIds)) {
                addImageFileName(imagesByMaintenanceId, image.itemMaintenanceId, image.fileName);
            }
            for (ItemMaintenance maintenance : page) {
                Row row = sheet.createRow(rowIndex++);
                columnChars[0] = Math.max(columnChars[0], writeLongCell(row, 0, maintenance.id));
                columnChars[1] = Math.max(columnChars[1],
                        writeLongCell(row, 1, maintenance.itemId));
                columnChars[2] = Math.max(columnChars[2],
                        writeStringCell(row, 2, resolveItemName(itemMap, maintenance.itemId)));
                columnChars[3] = Math.max(columnChars[3],
                        writeStringCell(row, 3, maintenance.description));
                columnChars[4] = Math.max(columnChars[4],
                        writeDecimalCell(row, 4, maintenance.cost));
                columnChars[5] = Math.max(columnChars[5],
                        writeDateCell(row, 5, maintenance.maintenanceDateTime, dateStyle));
                columnChars[6] = Math.max(columnChars[6],
                        writeDateCell(row, 6, maintenance.createdDateTime, dateStyle));
                columnChars[7] = Math.max(columnChars[7],
                        writeStringCell(row, 7, imagesByMaintenanceId.get(maintenance.id)));
            }
            lastId = page.get(page.size() - 1).id;
        }
        applyColumnWidths(sheet, columnChars);
    }

    /**
     * Reminders are keyset-paged like {@link #writeUsagesSheet} (see its javadoc);
     * they have no image table.
     */
    private void writeRemindersSheet(SXSSFWorkbook workbook, CellStyle headerStyle,
                                     CellStyle dateStyle, Map<Long, Item> itemMap, int sheetIndex) {
        emitSheetProgress(sheetIndex);
        Sheet sheet = workbook.createSheet(SHEET_REMINDERS);
        int[] columnChars = writeHeaderRow(sheet, headerStyle, new String[]{
                mAppContext.getString(R.string.header_id),
                mAppContext.getString(R.string.header_item_id),
                mAppContext.getString(R.string.header_item_name),
                mAppContext.getString(R.string.header_message),
                mAppContext.getString(R.string.header_reminder_date_time),
                mAppContext.getString(R.string.header_created_date_time),
        });
        int rowIndex = 1;
        long lastId = 0L;
        List<ItemReminder> page;
        while (!(page = mItemReminderDao.findItemRemindersAfter(lastId, PAGE_SIZE)).isEmpty()) {
            for (ItemReminder reminder : page) {
                Row row = sheet.createRow(rowIndex++);
                columnChars[0] = Math.max(columnChars[0], writeLongCell(row, 0, reminder.id));
                columnChars[1] = Math.max(columnChars[1], writeLongCell(row, 1, reminder.itemId));
                columnChars[2] = Math.max(columnChars[2],
                        writeStringCell(row, 2, resolveItemName(itemMap, reminder.itemId)));
                columnChars[3] = Math.max(columnChars[3],
                        writeStringCell(row, 3, reminder.message));
                columnChars[4] = Math.max(columnChars[4],
                        writeDateCell(row, 4, reminder.reminderDateTime, dateStyle));
                columnChars[5] = Math.max(columnChars[5],
                        writeDateCell(row, 5, reminder.createdDateTime, dateStyle));
            }
            lastId = page.get(page.size() - 1).id;
        }
        applyColumnWidths(sheet, columnChars);
    }

    private void writeChecklistsSheet(SXSSFWorkbook workbook, CellStyle headerStyle,
                                      CellStyle dateStyle, List<ItemChecklist> checklists,
                                      int sheetIndex) {
        emitSheetProgress(sheetIndex);
        Sheet sheet = workbook.createSheet(SHEET_CHECKLISTS);
        int[] columnChars = writeHeaderRow(sheet, headerStyle, new String[]{
                mAppContext.getString(R.string.header_id),
                mAppContext.getString(R.string.header_title),
                mAppContext.getString(R.string.form_description),
                mAppContext.getString(R.string.header_created_date_time),
                mAppContext.getString(R.string.header_updated_date_time),
        });
        int rowIndex = 1;
        for (ItemChecklist checklist : checklists) {
            Row row = sheet.createRow(rowIndex++);
            columnChars[0] = Math.max(columnChars[0], writeLongCell(row, 0, checklist.id));
            columnChars[1] = Math.max(columnChars[1], writeStringCell(row, 1, checklist.title));
            columnChars[2] = Math.max(columnChars[2],
                    writeStringCell(row, 2, checklist.description));
            columnChars[3] = Math.max(columnChars[3],
                    writeDateCell(row, 3, checklist.createdDateTime, dateStyle));
            columnChars[4] = Math.max(columnChars[4],
                    writeDateCell(row, 4, checklist.updatedDateTime, dateStyle));
        }
        applyColumnWidths(sheet, columnChars);
    }

    /**
     * Checklist items are keyset-paged like {@link #writeUsagesSheet} (see its
     * javadoc); checklist titles and item names resolve from the fully loaded
     * anchor maps.
     */
    private void writeChecklistItemsSheet(SXSSFWorkbook workbook, CellStyle headerStyle,
                                          CellStyle dateStyle,
                                          Map<Long, String> checklistTitleMap,
                                          Map<Long, Item> itemMap, int sheetIndex) {
        emitSheetProgress(sheetIndex);
        Sheet sheet = workbook.createSheet(SHEET_CHECKLIST_ITEMS);
        int[] columnChars = writeHeaderRow(sheet, headerStyle, new String[]{
                mAppContext.getString(R.string.header_id),
                mAppContext.getString(R.string.header_checklist_id),
                mAppContext.getString(R.string.header_title),
                mAppContext.getString(R.string.header_item_id),
                mAppContext.getString(R.string.header_item_name),
                mAppContext.getString(R.string.header_checked_date_time),
                mAppContext.getString(R.string.header_created_date_time),
        });
        int rowIndex = 1;
        long lastId = 0L;
        List<ItemChecklistItem> page;
        while (!(page = mItemChecklistDao.findItemChecklistItemsAfter(lastId, PAGE_SIZE)).isEmpty()) {
            for (ItemChecklistItem checklistItem : page) {
                Row row = sheet.createRow(rowIndex++);
                columnChars[0] = Math.max(columnChars[0],
                        writeLongCell(row, 0, checklistItem.id));
                columnChars[1] = Math.max(columnChars[1],
                        writeLongCell(row, 1, checklistItem.itemChecklistId));
                columnChars[2] = Math.max(columnChars[2],
                        writeStringCell(row, 2, checklistTitleMap.get(checklistItem.itemChecklistId)));
                columnChars[3] = Math.max(columnChars[3],
                        writeLongCell(row, 3, checklistItem.itemId));
                columnChars[4] = Math.max(columnChars[4],
                        writeStringCell(row, 4, resolveItemName(itemMap, checklistItem.itemId)));
                columnChars[5] = Math.max(columnChars[5],
                        writeDateCell(row, 5, checklistItem.checkedDateTime, dateStyle));
                columnChars[6] = Math.max(columnChars[6],
                        writeDateCell(row, 6, checklistItem.createdDateTime, dateStyle));
            }
            lastId = page.get(page.size() - 1).id;
        }
        applyColumnWidths(sheet, columnChars);
    }
}
