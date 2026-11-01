package m.co.rh.id.a_personal_stuff.app.provider.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.core.os.ConfigurationCompat;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONObject;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.entity.BackupData;
import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.base.backup.ExportImageDir;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleBackupContributor;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleExport;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSheetRow;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSheetRowSink;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSpreadsheetContributor;
import m.co.rh.id.a_personal_stuff.base.backup.ModuleSpreadsheetSheet;
import m.co.rh.id.a_personal_stuff.base.backup.SpreadsheetJoinData;
import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Cell;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.CellStyle;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Font;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Row;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.ss.usermodel.Sheet;
import m.co.rh.id.apoi_spreadsheet.org.apache.poi.xssf.streaming.SXSSFWorkbook;
import m.co.rh.id.aprovider.Provider;

/**
 * Stateless export builder: gathers the base-DB items core plus every
 * module contributor's sections and referenced images into a single zip
 * archive, renders the inventory's insurance-ready PDF report, and builds
 * the multi-sheet XLSX spreadsheet export. Holds only final injected
 * collaborators and is safe to share; callers own the threading (the work
 * is blocking).
 */
public class ExportService {

    /** Receives localized progress messages while an export is built. */
    public interface ProgressListener {
        void onProgress(String message);
    }

    private static final String BACKUP_JSON_ENTRY = "backup.json";
    private static final int BUFFER_SIZE = 2048;
    private static final String DATE_FORMAT = "yyyyMMdd_HHmmss";
    private static final String PDF_EXTENSION = ".pdf";
    private static final String TAG_SEPARATOR = ", ";
    /**
     * A4 in PostScript points at 72 dpi. {@link PdfDocument.PageInfo} measures
     * in POINTS, not device pixels — using pixel-typed constants here is the
     * classic bug that produces a tiny corner-sized page.
     */
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN = 40f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN;
    private static final float USABLE_HEIGHT = CONTENT_BOTTOM - MARGIN;
    /** Vertical center of the footer text on every page. */
    private static final float FOOTER_Y = PAGE_HEIGHT - 24f;
    /**
     * Thumbnail display box on detail pages: the 320x180 thumbnail aspect
     * (16:9), so thumbnail files map onto it without any rescaling.
     */
    private static final float THUMB_WIDTH = 96f;
    private static final float THUMB_HEIGHT = 54f;
    private static final float THUMB_GAP = 8f;
    /** 5 * 96 + 4 * 8 = 512 <= CONTENT_WIDTH (515): fills the line neatly. */
    private static final int THUMBS_PER_ROW = 5;
    /**
     * Decode target is ~2x the display box: enough headroom that the PDF
     * scaling stays crisp without decoding camera-sized originals.
     */
    private static final int DECODE_TARGET_WIDTH = (int) (THUMB_WIDTH * 2);
    private static final int DECODE_TARGET_HEIGHT = (int) (THUMB_HEIGHT * 2);
    /**
     * Upper bound of photos drawn per report (see the memory strategy in the
     * createReportPdf javadoc). Overridable per instance for the budget
     * instrumentation test via the budget constructor.
     */
    private static final int GLOBAL_IMAGE_BUDGET = 500;
    /** Detail/progress granularity: emit one progress message per 10 items. */
    private static final int PROGRESS_ITEM_STEP = 10;
    private static final float TABLE_ROW_HEIGHT = 18f;
    /** Minimum horizontal gap between summary-table columns. */
    private static final float COLUMN_GUTTER = 8f;
    /**
     * Floor for the elastic Name column: the measured numeric/date columns
     * are laid out from the right edge, and if their content ever left less
     * than this for the names, the table may run slightly past the right
     * margin instead of collapsing the names into an unreadable sliver.
     */
    private static final float TABLE_NAME_MIN_WIDTH = 60f;
    private static final float DESCRIPTION_BLOCK_PADDING = 3f;

    private static final String SHEET_ITEMS = "Items";
    private static final String XLSX_EXTENSION = ".xlsx";
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
    private final ItemDao mItemDao;
    private final ItemFileHelper mItemFileHelper;
    private final BackupContributorRegistry mContributorRegistry;
    private final ItemPurchaseDao mItemPurchaseDao;
    private final int mImageBudget;
    /** Same display formatting the dashboard's inventory value uses. */
    private final NumberFormat mPriceFormat;
    private final SimpleDateFormat mDateFormat;
    private final SimpleDateFormat mDateTimeFormat;

    public ExportService(Provider provider) {
        this(provider, GLOBAL_IMAGE_BUDGET);
    }

    /**
     * Budget-overridable constructor for the instrumentation test: a small
     * budget makes the "photos omitted" path observable without seeding
     * hundreds of real files. Public (rather than package-private like the
     * equivalent ExportPdfReportCmd constructor it replaces) because the test
     * lives in a different package.
     */
    public ExportService(Provider provider, int imageBudget) {
        mAppContext = provider.getContext().getApplicationContext();
        mFileHelper = provider.get(FileHelper.class);
        mItemDao = provider.get(ItemDao.class);
        mItemFileHelper = provider.get(ItemFileHelper.class);
        mContributorRegistry = provider.get(BackupContributorRegistry.class);
        mItemPurchaseDao = provider.get(ItemPurchaseDao.class);
        mImageBudget = imageBudget;
        Locale locale = ConfigurationCompat.getLocales(
                mAppContext.getResources().getConfiguration()).get(0);
        mPriceFormat = NumberFormat.getNumberInstance(locale);
        mPriceFormat.setMaximumFractionDigits(2);
        mDateFormat = new SimpleDateFormat("yyyy-MM-dd", locale);
        mDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", locale);
    }

    /**
     * Builds the full backup zip (backup.json + all referenced images) as a
     * temp file. Blocking; call from a background thread. The returned temp
     * file must be recycled by the caller via
     * {@code FileHelper.deleteTempFile} (or handed to the user).
     *
     * @param progressListener optional; receives localized progress messages
     */
    public File createBackupZip(ProgressListener progressListener) throws Exception {
        progress(progressListener, R.string.export_progress_gathering);
        BackupData backupData = gatherData();
        JSONObject root = backupData.toJson();

        // module sections and their referenced image dirs come from the
        // contributors; the base items core and its image dirs stay here
        // (the items anchor is :app-owned)
        List<ExportImageDir> imageDirs = new ArrayList<>();
        Set<String> itemImageFileNames = new HashSet<>();
        for (ItemImage img : backupData.itemImages) {
            if (img.fileName != null) itemImageFileNames.add(img.fileName);
        }
        imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_IMAGE,
                mItemFileHelper.getItemImageParent(), itemImageFileNames));
        imageDirs.add(new ExportImageDir(Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL,
                mItemFileHelper.getItemImageThumbnailParent(), itemImageFileNames));
        for (ModuleBackupContributor contributor : mContributorRegistry.getBackupContributors()) {
            ModuleExport moduleExport = contributor.exportData();
            moduleExport.mergeInto(root);
            imageDirs.addAll(moduleExport.getImageDirs());
        }

        progress(progressListener, R.string.export_progress_writing);
        String json = root.toString();
        File zipFile = mFileHelper.createTempFile("backup.zip");
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            ZipEntry jsonEntry = new ZipEntry(BACKUP_JSON_ENTRY);
            zos.putNextEntry(jsonEntry);
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            for (int i = 0; i < imageDirs.size(); i++) {
                progress(progressListener, R.string.export_progress_images, i + 1);
                ExportImageDir imageDir = imageDirs.get(i);
                addImageDirToZip(zos, imageDir.getParentDir(),
                        imageDir.getEntryPrefix(), imageDir.getReferencedFileNames());
            }
        } finally {
            zos.close();
        }
        return zipFile;
    }

    /**
     * Builds the backup zip without progress reporting. Convenience overload
     * for background callers (e.g. the auto backup worker).
     */
    public File createBackupZip() throws Exception {
        return createBackupZip(null);
    }

    /**
     * Renders the whole inventory as an insurance-ready PDF report (cover
     * totals, a per-item summary table and a photo-per-item detail section)
     * into a temp file, using {@link android.graphics.pdf.PdfDocument}.
     * Blocking; call from a background thread. The returned temp file must be
     * recycled by the caller via {@code FileHelper.deleteTempFile} (or handed
     * to the user).
     * <p>
     * Memory strategy (why this method is shaped the way it is): PdfDocument
     * has no streaming mode — every recorded page, including the encoded pixel
     * data of every drawn image, stays in RAM until the single final
     * {@link PdfDocument#writeTo(java.io.OutputStream)}. Consequences:
     * <ul>
     * <li>The file is written LAST, after all pages are recorded (mirroring the
     *     XLSX export's write-at-end), the partial file is deleted on a
     *     failed write, and the document is closed in {@code finally} (the analog
     *     of {@code workbook.dispose()}).</li>
     * <li>Images are the only unbounded cost, so decoding is strictly ONE bitmap
     *     at a time: each photo is decoded into a local variable inside the draw
     *     loop (no field or collection ever holds bitmaps) via a bounds-only pass
     *     that computes {@link BitmapFactory.Options#inSampleSize} against the
     *     ~2x display size, and every decode requests
     *     {@link Bitmap.Config#RGB_565} — item photos are opaque, so the dropped
     *     alpha channel halves the decode footprint.</li>
     * <li>After {@link PdfDocument#finishPage(PdfDocument.Page)} the bitmap's Java
     *     reference is dropped WITHOUT calling {@link Bitmap#recycle()}: whether
     *     the recorded page still reads the pixel buffer during
     *     {@code writeTo()} is not API-guaranteed on older Skia backends, so an
     *     early recycle can corrupt the output, while dropping the reference is
     *     never corrupt and frees the pixels as soon as the GC gets to it.</li>
     * <li>{@link #GLOBAL_IMAGE_BUDGET} caps how many photos one report may draw.
     *     Past the budget each photo position renders as a placeholder box with
     *     the localized "(photo omitted — image limit reached)" note, and the
     *     cover reports the total, so a large photo library degrades gracefully
     *     instead of OOMing the export. The budget counts drawable positions
     *     (a row whose file exists), which is also why the cover's omitted count
     *     is computed up front — the cover is page 1 and must be recorded before
     *     the detail pages are drawn.</li>
     * </ul>
     * <p>
     * Totals are never recomputed from the loaded list: the cover reads
     * {@link ItemDao#sumInventoryValue()} and {@link ItemDao#countItems()} — the
     * same single source of truth the dashboard uses — while items are
     * deliberately full-loaded (small anchor table, their rows feed every page).
     *
     * @param progressListener optional; receives localized progress messages
     */
    public File createReportPdf(ProgressListener progressListener) throws Exception {
        progress(progressListener, R.string.export_progress_gathering);
        // Items are deliberately full-loaded: a small anchor table whose rows
        // feed every page of the report (same rationale as the XLSX export).
        List<ItemState> itemStates = mItemDao.findItemStateWithLimit(Integer.MAX_VALUE, null);
        // Cover totals come from the dashboard's own queries — never recomputed
        // from the list, so the report can never disagree with the dashboard.
        Double totalInventoryValue = mItemDao.sumInventoryValue();
        int totalItems = mItemDao.countItems();
        Map<Long, ItemPurchase> firstPurchases = new HashMap<>();
        for (ItemPurchase itemPurchase : mItemPurchaseDao.findFirstPurchasesPerItem()) {
            firstPurchases.put(itemPurchase.itemId, itemPurchase);
        }
        int photosOmitted = countPhotosOmitted(itemStates);

        ReportWriter writer = new ReportWriter();
        File pdfFile;
        try {
            progress(progressListener, R.string.export_pdf_report_progress_cover);
            writer.drawCoverPage(totalItems, totalInventoryValue, photosOmitted);

            progress(progressListener, R.string.export_pdf_report_progress_table);
            writer.drawSummaryTable(itemStates, firstPurchases);

            // one details message per 10-item batch (the first batch reports
            // from 0, so the cover/table stages above stay distinct events)
            int index = 0;
            for (ItemState itemState : itemStates) {
                if (index % PROGRESS_ITEM_STEP == 0) {
                    progress(progressListener,
                            R.string.export_pdf_report_progress_details,
                            index, itemStates.size());
                }
                writer.drawItemDetail(itemState, firstPurchases.get(itemState.getItem().id));
                index++;
            }
            writer.finishLastPage();

            progress(progressListener, R.string.export_pdf_report_progress_writing);
            String fileName = "personal_stuff_report_"
                    + new SimpleDateFormat(DATE_FORMAT, Locale.US).format(new Date())
                    + PDF_EXTENSION;
            pdfFile = mFileHelper.createTempFile(fileName);
            try {
                try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                    writer.mPdfDocument.writeTo(fos);
                }
            } catch (Exception e) {
                // delete the partially written file so no broken .pdf is left behind
                pdfFile.delete();
                throw e;
            }
        } finally {
            // the analog of workbook.dispose(): releases the recorded pages
            writer.mPdfDocument.close();
        }
        return pdfFile;
    }

    /**
     * Builds the whole inventory as a multi-sheet XLSX spreadsheet (the
     * :app-owned Items sheet plus every module contributor's sheets) into a
     * temp file, using the streaming {@link SXSSFWorkbook}. Requires API 26
     * or higher (the POI spreadsheet library does not support lower API
     * levels). Blocking; call from a background thread. The returned temp
     * file must be recycled by the caller via {@code FileHelper.deleteTempFile}
     * (or handed to the user).
     * <p>
     * Memory strategy: Items are deliberately full-loaded because they are a
     * small anchor table whose rows also feed the item-name join map shared
     * with every module sheet. The high-volume tables (item_usage,
     * item_purchase, item_maintenance, item_reminder, item_checklist_item and
     * the three image tables) are keyset-paged by their contributors
     * ({@link ModuleSpreadsheetContributor#PAGE_SIZE} rows per page): a page
     * is loaded, written to the sheet, then dropped, which bounds peak memory
     * regardless of table size.
     *
     * @param progressListener optional; receives localized progress messages
     */
    public File createSpreadsheetXlsx(ProgressListener progressListener) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw new IllegalStateException("XLSX export requires Android 8.0 or newer");
        }
        progress(progressListener, R.string.export_progress_gathering);
        // The Items sheet is :app-owned (base-DB anchor). Items are
        // deliberately full-loaded: their rows feed the Items sheet AND the
        // item-name join map shared with every module sheet.
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
        SpreadsheetJoinData joinData = new SpreadsheetJoinData(itemMap);

        // Items sheet (:app-owned) plus every contributed sheet — derived at
        // run time so the progress denominator can never drift from the
        // actual sheet count
        int totalSheets = 1;
        for (ModuleSpreadsheetContributor contributor : mContributorRegistry.getSpreadsheetContributors()) {
            totalSheets += contributor.getSheets().size();
        }

        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        File xlsxFile;
        try {
            SpreadsheetWriter writer = new SpreadsheetWriter(progressListener, totalSheets, workbook);

            writer.writeItemsSheet(workbook, itemStates, itemTagsMap, itemImagesByItemId, 1);
            // module sheets in contributor registration order (usage,
            // purchase, maintenance, reminder, checklist) followed by each
            // contributor's own sheet order (checklist: Checklists, Checklist
            // Items) — keeping the sheet order of the pre-contract format.
            int sheetIndex = 2;
            for (ModuleSpreadsheetContributor contributor
                    : mContributorRegistry.getSpreadsheetContributors()) {
                for (ModuleSpreadsheetSheet sheet : contributor.getSheets()) {
                    writer.writeContributedSheet(workbook, sheet, joinData, sheetIndex++);
                }
            }

            progress(progressListener, R.string.export_spreadsheet_progress_writing);
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

    /**
     * Pre-computes how many drawable photo positions the image budget will
     * skip, so the cover (which is recorded before the detail pages) can
     * state the total. A position is drawable when the image row has a file
     * name and at least one of its files exists on disk; rows without files
     * render as a plain placeholder and never count against the budget.
     */
    private int countPhotosOmitted(List<ItemState> itemStates) {
        int drawablePositions = 0;
        for (ItemState itemState : itemStates) {
            List<ItemImage> itemImages = itemState.getItemImages();
            if (itemImages == null) {
                continue;
            }
            for (ItemImage itemImage : itemImages) {
                String fileName = itemImage.fileName;
                if (fileName == null || fileName.isEmpty()) {
                    continue;
                }
                if (mItemFileHelper.getItemImageThumbnail(fileName).exists()
                        || mItemFileHelper.getItemImage(fileName).exists()) {
                    drawablePositions++;
                }
            }
        }
        int omitted = drawablePositions - mImageBudget;
        return Math.max(0, omitted);
    }

    private void progress(ProgressListener listener, int resId, Object... args) {
        if (listener != null) {
            listener.onProgress(mAppContext.getString(resId, args));
        }
    }

    private BackupData gatherData() {
        BackupData data = new BackupData();
        List<ItemState> itemStates = mItemDao.findItemStateWithLimit(Integer.MAX_VALUE, null);
        for (ItemState state : itemStates) {
            data.items.add(state.getItem());
            data.itemImages.addAll(state.getItemImages());
            data.itemTags.addAll(state.getItemTags());
        }
        return data;
    }

    private void addImageDirToZip(ZipOutputStream zos, File imageDir, String entryPrefix, Set<String> referencedFileNames) throws Exception {
        if (imageDir == null || !imageDir.exists()) return;
        File[] files = imageDir.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[BUFFER_SIZE];
        for (File file : files) {
            if (file.isDirectory()) continue;
            if (referencedFileNames != null && !referencedFileNames.contains(file.getName())) continue;
            ZipEntry entry = new ZipEntry(entryPrefix + "/" + file.getName());
            zos.putNextEntry(entry);
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE);
            try {
                int count;
                while ((count = bis.read(buffer, 0, BUFFER_SIZE)) != -1) {
                    zos.write(buffer, 0, count);
                }
            } finally {
                bis.close();
                zos.closeEntry();
            }
        }
    }

    /**
     * Joins an item's tags with the shared tag separator (blank when the item
     * has none) for the Items sheet's Tags column.
     */
    private static String joinTags(TreeSet<ItemTag> itemTags) {
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
    private static void addImageFileName(Map<Long, String> imagesByParentId, Long parentId, String fileName) {
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

    private static CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFont(font);
        return cellStyle;
    }

    private static CellStyle createDateStyle(SXSSFWorkbook workbook) {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd HH:mm:ss"));
        return cellStyle;
    }

    /**
     * Writes the header row and returns one display-length entry per column
     * (seeded with each header's length) so the caller can track the widest
     * content per column for the auto-computed column widths.
     */
    private static int[] writeHeaderRow(Sheet sheet, CellStyle headerStyle, String[] headers) {
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
    private static int writeLongCell(Row row, int column, Long value) {
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
    private static int writeStringCell(Row row, int column, String value) {
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
    private static int writeAmountCell(Row row, int column, int value) {
        row.createCell(column).setCellValue(value);
        return String.valueOf(value).length();
    }

    /**
     * Writes a nullable integer amount cell: null writes a blank cell,
     * otherwise the cell stays a true numeric cell, tracking the column width
     * like {@link #writeAmountCell(Row, int, int)}.
     *
     * @return the display length of the written value, 0 when null.
     */
    private static int writeNullableAmountCell(Row row, int column, Integer value) {
        if (value == null) {
            return 0;
        }
        row.createCell(column).setCellValue(value.doubleValue());
        return String.valueOf(value).length();
    }

    /**
     * @return the approximate display length of the written value, 0 when null.
     */
    private static int writeDecimalCell(Row row, int column, BigDecimal value) {
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
    private static int writeDateCell(Row row, int column, Date value, CellStyle dateStyle) {
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

    /**
     * Per-run drawing state: pages, cursor, paints and the image budget
     * counter live here instead of on the service so a failed run can never
     * leak page/canvas state into a retry (the caller's in-flight shell
     * serializes runs, but the service itself outlives them). Also owns all
     * pagination: every draw helper goes through {@link #ensureSpace(float)},
     * which finishes the current page (footer included) and starts the next
     * one when content no longer fits, so callers never handle page breaks
     * themselves.
     */
    private class ReportWriter {
        final PdfDocument mPdfDocument = new PdfDocument();
        PdfDocument.Page mCurrentPage;
        Canvas mCanvas;
        /** Top y of the next block on the current page. */
        float mCursorY;
        int mPageNumber;
        /** Photos actually drawn so far (budget counter). */
        int mImagesDrawn;

        final TextPaint mTitlePaint = newTextPaint(true, 22f);
        final TextPaint mNamePaint = newTextPaint(true, 12f);
        final TextPaint mTextPaint = newTextPaint(false, 10f);
        final TextPaint mTagPaint = newTextPaint(false, 10f);
        final TextPaint mTableHeaderPaint = newTextPaint(true, 10f);
        final TextPaint mSmallPaint = newTextPaint(false, 6.5f);
        final Paint mRulePaint;
        final Paint mBoxPaint;
        final Paint mBitmapPaint;
        private Paint mDescriptionBackgroundPaint;

        ReportWriter() {
            mRulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mRulePaint.setColor(Color.LTGRAY);
            mRulePaint.setStrokeWidth(0.5f);
            mBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBoxPaint.setColor(Color.GRAY);
            mBoxPaint.setStyle(Paint.Style.STROKE);
            mBoxPaint.setStrokeWidth(0.8f);
            mBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            mTagPaint.setColor(Color.GRAY);
        }

        void newPage() {
            mCurrentPage = mPdfDocument.startPage(
                    new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++mPageNumber).create());
            mCanvas = mCurrentPage.getCanvas();
            mCursorY = MARGIN;
        }

        /**
         * Reserves {@code neededHeight} points of vertical space on the open
         * page: finishes the current page (with its footer) and starts a fresh
         * one when the space is not available. Called before every block so
         * pages flow automatically; blocks taller than a full page simply
         * continue on the next one piece by piece.
         */
        void ensureSpace(float neededHeight) {
            if (mCurrentPage != null && mCursorY + neededHeight <= CONTENT_BOTTOM) {
                return;
            }
            if (mCurrentPage != null) {
                finishCurrentPage();
            }
            newPage();
        }

        void finishCurrentPage() {
            String footer = mAppContext.getString(R.string.report_page, mPageNumber);
            float footerWidth = mTextPaint.measureText(footer);
            mCanvas.drawText(footer, (PAGE_WIDTH - footerWidth) / 2f, FOOTER_Y, mTextPaint);
            mPdfDocument.finishPage(mCurrentPage);
            // the page recording is closed; drop both references (the bitmap
            // memory rules are documented on createReportPdf)
            mCurrentPage = null;
            mCanvas = null;
        }

        void finishLastPage() {
            if (mCurrentPage != null) {
                finishCurrentPage();
            }
        }

        void drawCoverPage(int totalItems, Double totalInventoryValue, int photosOmitted) {
            newPage();
            float y = 160f;
            String title = mAppContext.getString(R.string.report_title);
            mTitlePaint.setTextAlign(Paint.Align.CENTER);
            mCanvas.drawText(title, PAGE_WIDTH / 2f, y, mTitlePaint);
            mTitlePaint.setTextAlign(Paint.Align.LEFT);
            y += 60f;
            y = drawCenteredCoverLine(y, mAppContext.getString(
                    R.string.report_generated_date_time, mDateTimeFormat.format(new Date())));
            y = drawCenteredCoverLine(y, mAppContext.getString(R.string.report_total_items, totalItems));
            if (totalInventoryValue != null) {
                y = drawCenteredCoverLine(y, mAppContext.getString(
                        R.string.report_total_value,
                        mPriceFormat.format(BigDecimal.valueOf(totalInventoryValue))));
            } else {
                // every price is null — mirror the dashboard's "-" placeholder
                y = drawCenteredCoverLine(y, mAppContext.getString(
                        R.string.report_total_value, "-"));
            }
            if (photosOmitted > 0) {
                drawCenteredCoverLine(y, mAppContext.getString(
                        R.string.report_photos_omitted, photosOmitted));
            }
            finishCurrentPage();
        }

        private float drawCenteredCoverLine(float y, String line) {
            mCanvas.drawText(line, PAGE_WIDTH / 2f, y, mTextPaint);
            return y + 28f;
        }

        void drawSummaryTable(List<ItemState> itemStates,
                              Map<Long, ItemPurchase> firstPurchases) {
            // PASS 1: build every cell (headers, data rows, totals row) and
            // measure the widest text per column BEFORE drawing anything. The
            // first live run proved fixed column guesses collide — the
            // right-aligned "Line total" value ended exactly where the "First
            // purchase" column began ("899.52026-11-01") — so the x offsets
            // are now DERIVED from the measured widths plus a minimum gutter.
            int dataRowCount = itemStates.size();
            int rowCount = dataRowCount + 1; // + totals row
            String[] noCells = new String[rowCount];
            String[] nameCells = new String[rowCount];
            String[] qtyCells = new String[rowCount];
            String[] unitCells = new String[rowCount];
            String[] lineCells = new String[rowCount];
            String[] purchaseCells = new String[rowCount];
            BigDecimal grandTotal = BigDecimal.ZERO;
            for (int i = 0; i < dataRowCount; i++) {
                Item item = itemStates.get(i).getItem();
                noCells[i] = String.valueOf(i + 1);
                nameCells[i] = item.name == null ? "" : item.name;
                qtyCells[i] = String.valueOf(item.amount);
                if (item.price != null) {
                    BigDecimal lineTotal = item.price.multiply(BigDecimal.valueOf(item.amount));
                    grandTotal = grandTotal.add(lineTotal);
                    unitCells[i] = mPriceFormat.format(item.price);
                    lineCells[i] = mPriceFormat.format(lineTotal);
                } else {
                    unitCells[i] = "";
                    lineCells[i] = "";
                }
                purchaseCells[i] = formatTableFirstPurchase(firstPurchases.get(item.id));
            }
            // totals row: Σ line totals (null prices contributed nothing)
            noCells[dataRowCount] = "";
            nameCells[dataRowCount] = mAppContext.getString(R.string.report_totals);
            qtyCells[dataRowCount] = "";
            unitCells[dataRowCount] = "";
            lineCells[dataRowCount] = mPriceFormat.format(grandTotal);
            purchaseCells[dataRowCount] = "";
            String[] headerCells = new String[]{
                    mAppContext.getString(R.string.report_col_no),
                    mAppContext.getString(R.string.report_col_name),
                    mAppContext.getString(R.string.report_col_qty),
                    mAppContext.getString(R.string.report_col_unit_price),
                    mAppContext.getString(R.string.report_col_line_total),
                    mAppContext.getString(R.string.report_col_first_purchase)};

            // PASS 2: lay out and draw. The layout is computed once for ALL
            // rows, so every page the table spans uses identical columns.
            TableLayout layout = computeTableLayout(headerCells,
                    noCells, nameCells, qtyCells, unitCells, lineCells, purchaseCells);
            newPage();
            drawTableHeader(layout);
            for (int i = 0; i < dataRowCount; i++) {
                ensureTableSpace(layout);
                drawTableRow(layout, noCells[i], nameCells[i], qtyCells[i],
                        unitCells[i], lineCells[i], purchaseCells[i]);
            }
            ensureTableSpace(layout);
            drawTotalsRow(layout, nameCells[dataRowCount], lineCells[dataRowCount]);
            finishCurrentPage();
        }

        /**
         * Derives the summary-table column positions from the measured max
         * text width of every cell (header, data and totals cells, measured
         * in BOTH the regular and the bold paint). The Name column is
         * elastic: it takes whatever the measured columns leave and its
         * content is ellipsized at draw time; the numeric/date columns are
         * right-aligned to their derived right edges, so a wide value can
         * never reach the neighboring column — every pair of columns is
         * separated by {@link #COLUMN_GUTTER} by construction.
         */
        private TableLayout computeTableLayout(String[] headerCells,
                String[] noCells, String[] nameCells, String[] qtyCells,
                String[] unitCells, String[] lineCells, String[] purchaseCells) {
            float widthNo = maxCellWidth(headerCells[0], noCells);
            float widthQty = maxCellWidth(headerCells[2], qtyCells);
            float widthUnit = maxCellWidth(headerCells[3], unitCells);
            float widthLine = maxCellWidth(headerCells[4], lineCells);
            float widthPurchase = maxCellWidth(headerCells[5], purchaseCells);
            float fixedTotal = widthNo + widthQty + widthUnit + widthLine
                    + widthPurchase + 5 * COLUMN_GUTTER;
            TableLayout layout = new TableLayout();
            layout.xNo = MARGIN;
            layout.xName = layout.xNo + widthNo + COLUMN_GUTTER;
            layout.nameWidth = Math.max(TABLE_NAME_MIN_WIDTH,
                    CONTENT_WIDTH - fixedTotal);
            layout.qtyRight = layout.xName + layout.nameWidth + COLUMN_GUTTER + widthQty;
            layout.unitRight = layout.qtyRight + COLUMN_GUTTER + widthUnit;
            layout.lineRight = layout.unitRight + COLUMN_GUTTER + widthLine;
            layout.xPurchase = layout.lineRight + COLUMN_GUTTER;
            return layout;
        }

        private float maxCellWidth(String header, String[] cells) {
            float width = Math.max(mTextPaint.measureText(header),
                    mTableHeaderPaint.measureText(header));
            for (String cell : cells) {
                if (cell == null || cell.isEmpty()) {
                    continue;
                }
                width = Math.max(width, mTextPaint.measureText(cell));
                width = Math.max(width, mTableHeaderPaint.measureText(cell));
            }
            return width;
        }

        /**
         * Table rows page-break with a repeated header: unlike
         * {@link #ensureSpace(float)} a plain new page is not enough, the
         * column headers must be redrawn first for the table to stay
         * readable across pages.
         */
        private void ensureTableSpace(TableLayout layout) {
            if (mCurrentPage != null && mCursorY + TABLE_ROW_HEIGHT <= CONTENT_BOTTOM) {
                return;
            }
            if (mCurrentPage != null) {
                finishCurrentPage();
            }
            newPage();
            drawTableHeader(layout);
        }

        private void drawTableHeader(TableLayout layout) {
            float baseline = rowBaseline(mCursorY, mTableHeaderPaint);
            mCanvas.drawText(mAppContext.getString(R.string.report_col_no),
                    layout.xNo, baseline, mTableHeaderPaint);
            mCanvas.drawText(mAppContext.getString(R.string.report_col_name),
                    layout.xName, baseline, mTableHeaderPaint);
            drawRightAligned(mAppContext.getString(R.string.report_col_qty),
                    layout.qtyRight, baseline, mTableHeaderPaint);
            drawRightAligned(mAppContext.getString(R.string.report_col_unit_price),
                    layout.unitRight, baseline, mTableHeaderPaint);
            drawRightAligned(mAppContext.getString(R.string.report_col_line_total),
                    layout.lineRight, baseline, mTableHeaderPaint);
            mCanvas.drawText(mAppContext.getString(R.string.report_col_first_purchase),
                    layout.xPurchase, baseline, mTableHeaderPaint);
            mCursorY += TABLE_ROW_HEIGHT;
            drawHorizontalRule();
        }

        private void drawTableRow(TableLayout layout, String no, String name, String qty,
                                  String unitPrice, String lineTotal, String firstPurchase) {
            float baseline = rowBaseline(mCursorY, mTextPaint);
            mCanvas.drawText(no, layout.xNo, baseline, mTextPaint);
            // TextUtils.ellipsize rejects null, so a name-less row exports blank
            CharSequence ellipsizedName = TextUtils.ellipsize(name, mTextPaint,
                    layout.nameWidth, TextUtils.TruncateAt.END);
            mCanvas.drawText(ellipsizedName.toString(), layout.xName, baseline, mTextPaint);
            drawRightAligned(qty, layout.qtyRight, baseline, mTextPaint);
            drawRightAligned(unitPrice, layout.unitRight, baseline, mTextPaint);
            drawRightAligned(lineTotal, layout.lineRight, baseline, mTextPaint);
            mCanvas.drawText(firstPurchase, layout.xPurchase, baseline, mTextPaint);
            mCursorY += TABLE_ROW_HEIGHT;
            drawHorizontalRule();
        }

        private String formatTableFirstPurchase(ItemPurchase firstPurchase) {
            if (firstPurchase == null || firstPurchase.purchaseDateTime == null) {
                return "";
            }
            return mDateFormat.format(firstPurchase.purchaseDateTime);
        }

        private void drawTotalsRow(TableLayout layout, String label, String totalValue) {
            float baseline = rowBaseline(mCursorY, mTableHeaderPaint);
            mCanvas.drawText(label, layout.xName, baseline, mTableHeaderPaint);
            drawRightAligned(totalValue, layout.lineRight, baseline, mTableHeaderPaint);
            mCursorY += TABLE_ROW_HEIGHT;
            drawHorizontalRule();
        }

        /**
         * Draws one item's detail block: the identity heading + tags, then
         * ALL of the item's photos as a thumbnail grid, then the full
         * description as a prominent wrapped block, then the valuation/stats
         * lines. Photos come from the existing thumbnail files; a missing
         * thumbnail falls back to the full image (decoded down with
         * inSampleSize); a missing file renders as a placeholder box. Each
         * photo position is budget-checked right before it is drawn (see the
         * createReportPdf memory strategy).
         */
        void drawItemDetail(ItemState itemState, ItemPurchase firstPurchase) {
            Item item = itemState.getItem();
            // Reserve the block's space first when it can fit on one page at
            // all, so an item never starts at the very bottom; taller blocks
            // flow across pages piece by piece instead.
            float blockHeight = measureDetailBlockHeight(itemState, firstPurchase);
            ensureSpace(Math.min(blockHeight, USABLE_HEIGHT));

            // reserve the spacer + the full name band so the heading can
            // never be clipped at a page bottom
            ensureSpace(28f);
            mCursorY += 8f;
            mCanvas.drawText(item.name == null ? "" : item.name,
                    MARGIN, rowBaseline(mCursorY, mNamePaint), mNamePaint);
            mCursorY += 20f;

            String tags = joinTags(itemState);
            if (!tags.isEmpty()) {
                ensureSpace(18f);
                mCanvas.drawText(tags, MARGIN, rowBaseline(mCursorY, mTagPaint), mTagPaint);
                mCursorY += 18f;
            }

            // The photo grid must NOT precede the name: photos drawn at the
            // start of a block sit directly under the PREVIOUS item's stats
            // line and read as that item's photos (v2 device render attached
            // CAM 1/CAM 2 to Green Tea Packs and MYSTERY FULL to Canon) — in
            // a claims document every photo has to sit inside its owning
            // block, directly under the name it belongs to.
            drawImageGrid(itemState);

            drawDescriptionBlock(item.description);

            if (item.barcode != null && !item.barcode.isEmpty()) {
                drawTextLine(mAppContext.getString(R.string.barcode_, item.barcode));
            }
            if (item.price != null) {
                drawTextLine(mAppContext.getString(R.string.report_item_amount_price,
                        item.amount, mPriceFormat.format(item.price),
                        mPriceFormat.format(item.price.multiply(BigDecimal.valueOf(item.amount)))));
            } else {
                // a null price shows the amount alone, consistent with the
                // blank cells the summary table uses for unpriced items
                drawTextLine(mAppContext.getString(R.string.amount_, item.amount));
            }
            if (firstPurchase != null && firstPurchase.purchaseDateTime != null) {
                drawTextLine(mAppContext.getString(R.string.report_first_purchase,
                        mDateFormat.format(firstPurchase.purchaseDateTime),
                        firstPurchase.cost == null ? "-" : mPriceFormat.format(firstPurchase.cost)));
            }
            if (item.expiredDateTime != null) {
                drawTextLine(mAppContext.getString(R.string.report_expires,
                        mDateFormat.format(item.expiredDateTime)));
            }
            if (item.createdDateTime != null) {
                drawTextLine(mAppContext.getString(R.string.report_created,
                        mDateTimeFormat.format(item.createdDateTime)));
            }
            ensureSpace(20f);
            mCursorY += 12f;
            mCanvas.drawLine(MARGIN, mCursorY, PAGE_WIDTH - MARGIN, mCursorY, mRulePaint);
            mCursorY += 1f;
        }

        private float measureDetailBlockHeight(ItemState itemState, ItemPurchase firstPurchase) {
            Item item = itemState.getItem();
            int imageRows = 0;
            List<ItemImage> itemImages = itemState.getItemImages();
            if (itemImages != null) {
                int validImages = 0;
                for (ItemImage itemImage : itemImages) {
                    if (itemImage.fileName != null && !itemImage.fileName.isEmpty()) {
                        validImages++;
                    }
                }
                imageRows = (validImages + THUMBS_PER_ROW - 1) / THUMBS_PER_ROW;
            }
            float height = imageRows * (THUMB_HEIGHT + THUMB_GAP) + 28f;
            if (itemState.getItemTags() != null && !itemState.getItemTags().isEmpty()) {
                height += 18f;
            }
            if (item.description != null && !item.description.trim().isEmpty()) {
                StaticLayout layout = buildDescriptionLayout(item.description);
                height += layout.getHeight() + 2 * DESCRIPTION_BLOCK_PADDING + 6f;
            }
            if (item.barcode != null && !item.barcode.isEmpty()) {
                height += 14f;
            }
            height += 14f;
            if (firstPurchase != null && firstPurchase.purchaseDateTime != null) {
                height += 14f;
            }
            if (item.expiredDateTime != null) {
                height += 14f;
            }
            if (item.createdDateTime != null) {
                height += 14f;
            }
            return height + 33f;
        }

        /**
         * Draws the item's photos as a grid of uniform thumbnail boxes. Every
         * path — thumbnail file, full-image fallback (decoded down with
         * inSampleSize), missing-file or budget-exceeded placeholder —
         * funnels into the SAME {@link #THUMB_WIDTH}x{@link #THUMB_HEIGHT}
         * fit-center rect, so a fallback photo can never render at a
         * different size than a thumbnail photo. The cursor always advances
         * past the finished row before the method returns, so the description
         * block drawn below can never end up under a bitmap.
         */
        private void drawImageGrid(ItemState itemState) {
            List<ItemImage> itemImages = itemState.getItemImages();
            if (itemImages == null) {
                return;
            }
            int col = 0;
            float rowTop = mCursorY;
            for (ItemImage itemImage : itemImages) {
                String fileName = itemImage.fileName;
                if (fileName == null || fileName.isEmpty()) {
                    continue;
                }
                if (col == 0) {
                    ensureSpace(THUMB_HEIGHT + THUMB_GAP);
                    rowTop = mCursorY;
                }
                float left = MARGIN + col * (THUMB_WIDTH + THUMB_GAP);
                File thumbnail = mItemFileHelper.getItemImageThumbnail(fileName);
                File full = mItemFileHelper.getItemImage(fileName);
                if (!thumbnail.exists() && !full.exists()) {
                    // no file at all: plain placeholder, no budget consumption
                    drawPlaceholderBox(left, rowTop, false);
                } else if (mImagesDrawn >= mImageBudget) {
                    drawPlaceholderBox(left, rowTop, true);
                } else {
                    Bitmap bitmap = decodeForReport(thumbnail.exists() ? thumbnail : full);
                    if (bitmap != null) {
                        drawBitmapFitCenter(bitmap, left, rowTop);
                        mImagesDrawn++;
                        // drop the local reference — NEVER recycle here (the
                        // recorded page may still be read during writeTo)
                        bitmap = null;
                    } else {
                        drawPlaceholderBox(left, rowTop, false);
                    }
                }
                col++;
                if (col == THUMBS_PER_ROW) {
                    col = 0;
                    mCursorY = rowTop + THUMB_HEIGHT + THUMB_GAP;
                }
            }
            if (col > 0) {
                mCursorY = rowTop + THUMB_HEIGHT + THUMB_GAP;
            }
        }

        /**
         * Draws the FULL description untruncated as a visually prominent
         * (background-blocked) wrapped text. The description carries the
         * serial/room/condition/warranty notes, so it is never ellipsized;
         * StaticLayout chunks are drawn line-range by line-range so a long
         * description flows across page breaks like every other block.
         */
        @SuppressWarnings("deprecation")
        private void drawDescriptionBlock(String description) {
            if (description == null || description.trim().isEmpty()) {
                return;
            }
            StaticLayout layout = buildDescriptionLayout(description);
            int line = 0;
            int lineCount = layout.getLineCount();
            while (line < lineCount) {
                float lineTop = layout.getLineTop(line);
                // reserve at least the current line's height so the loop
                // always makes progress, even across page breaks
                ensureSpace(layout.getLineBottom(line) - lineTop);
                float available = CONTENT_BOTTOM - mCursorY;
                int endLine = line + 1;
                while (endLine < lineCount
                        && layout.getLineBottom(endLine) - lineTop <= available) {
                    endLine++;
                }
                float drawnBottom = layout.getLineBottom(endLine - 1);
                float drawnHeight = drawnBottom - lineTop;
                // background block for this chunk, padded a little so the
                // text never touches the block edge
                mCanvas.drawRect(MARGIN - DESCRIPTION_BLOCK_PADDING,
                        mCursorY - DESCRIPTION_BLOCK_PADDING,
                        MARGIN + CONTENT_WIDTH + DESCRIPTION_BLOCK_PADDING,
                        mCursorY + drawnHeight + DESCRIPTION_BLOCK_PADDING,
                        mDescriptionBackgroundPaint());
                mCanvas.save();
                mCanvas.translate(MARGIN, mCursorY - lineTop);
                mCanvas.clipRect(0, lineTop, CONTENT_WIDTH, drawnBottom);
                layout.draw(mCanvas);
                mCanvas.restore();
                mCursorY += drawnHeight + 2 * DESCRIPTION_BLOCK_PADDING;
                line = endLine;
            }
            mCursorY += 6f;
        }

        @SuppressWarnings("deprecation")
        private StaticLayout buildDescriptionLayout(String description) {
            // the legacy StaticLayout constructor (API 1) is used instead of
            // the Builder (API 23) because minSdk is 21
            return new StaticLayout(description, mTextPaint, (int) CONTENT_WIDTH,
                    Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        }

        private Paint mDescriptionBackgroundPaint() {
            if (mDescriptionBackgroundPaint == null) {
                mDescriptionBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                mDescriptionBackgroundPaint.setColor(Color.rgb(0xF0, 0xF0, 0xF0));
            }
            return mDescriptionBackgroundPaint;
        }

        private void drawTextLine(String text) {
            ensureSpace(14f);
            mCanvas.drawText(text, MARGIN, rowBaseline(mCursorY, mTextPaint), mTextPaint);
            mCursorY += 14f;
        }

        /**
         * Decodes one report image: bounds pass first, then a downsampled
         * decode into RGB_565. Returns null when the file is unreadable (the
         * caller falls back to a placeholder). One bitmap at a time — see the
         * createReportPdf memory strategy.
         */
        private Bitmap decodeForReport(File file) {
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), boundsOptions);
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = computeInSampleSize(boundsOptions,
                    DECODE_TARGET_WIDTH, DECODE_TARGET_HEIGHT);
            // photos have no alpha — RGB_565 halves the decode footprint
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        }

        private void drawBitmapFitCenter(Bitmap bitmap, float left, float top) {
            float scale = Math.min(THUMB_WIDTH / bitmap.getWidth(),
                    THUMB_HEIGHT / bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            RectF dest = new RectF(left + (THUMB_WIDTH - width) / 2f,
                    top + (THUMB_HEIGHT - height) / 2f,
                    left + (THUMB_WIDTH + width) / 2f,
                    top + (THUMB_HEIGHT + height) / 2f);
            mCanvas.drawBitmap(bitmap, null, dest, mBitmapPaint);
        }

        /**
         * Largest power-of-two downsample that still decodes at or above the
         * target size (the standard BitmapFactory recipe): the next power of
         * two would fall below the display size and look soft.
         */
        private static int computeInSampleSize(BitmapFactory.Options options,
                                               int targetWidth, int targetHeight) {
            int inSampleSize = 1;
            int halfWidth = options.outWidth / 2;
            int halfHeight = options.outHeight / 2;
            while ((halfWidth / inSampleSize) >= targetWidth
                    && (halfHeight / inSampleSize) >= targetHeight) {
                inSampleSize *= 2;
            }
            return inSampleSize;
        }

        private void drawPlaceholderBox(float left, float top, boolean withOmittedNote) {
            mCanvas.drawRect(left, top, left + THUMB_WIDTH, top + THUMB_HEIGHT, mBoxPaint);
            if (withOmittedNote) {
                String note = mAppContext.getString(R.string.report_photo_omitted);
                CharSequence ellipsized = TextUtils.ellipsize(note, mSmallPaint,
                        THUMB_WIDTH - 6f, TextUtils.TruncateAt.END);
                float noteWidth = mSmallPaint.measureText(ellipsized.toString());
                mCanvas.drawText(ellipsized.toString(),
                        left + (THUMB_WIDTH - noteWidth) / 2f,
                        rowBaseline(top, THUMB_HEIGHT, mSmallPaint), mSmallPaint);
            }
        }

        private float rowBaseline(float rowTop, Paint paint) {
            return rowBaseline(rowTop, TABLE_ROW_HEIGHT, paint);
        }

        /** Baseline that vertically centers a text line inside the given band. */
        private float rowBaseline(float rowTop, float height, Paint paint) {
            Paint.FontMetrics fontMetrics = paint.getFontMetrics();
            // (height - lineHeight) / 2 centers the glyph box in the band; the
            // earlier (height - bottom - top) variant double-counted the
            // ascent and drew every line roughly one ascent too low, so the
            // description background rect (drawn at cursor - padding) painted
            // over the item name above it (live-tested bug: detail heading
            // vanished on items whose description follows the name directly)
            return rowTop + (height - (fontMetrics.bottom - fontMetrics.top)) / 2f
                    - fontMetrics.top;
        }

        private void drawRightAligned(String text, float columnRight, float baseline,
                                      TextPaint paint) {
            mCanvas.drawText(text, columnRight - paint.measureText(text), baseline, paint);
        }

        private void drawHorizontalRule() {
            mCanvas.drawLine(MARGIN, mCursorY, PAGE_WIDTH - MARGIN, mCursorY, mRulePaint);
        }

        private String joinTags(ItemState itemState) {
            if (itemState.getItemTags() == null || itemState.getItemTags().isEmpty()) {
                return "";
            }
            StringBuilder stringBuilder = new StringBuilder();
            for (ItemTag itemTag : itemState.getItemTags()) {
                if (stringBuilder.length() > 0) {
                    stringBuilder.append(TAG_SEPARATOR);
                }
                stringBuilder.append(itemTag.tag);
            }
            return stringBuilder.toString();
        }

        private static TextPaint newTextPaint(boolean bold, float textSize) {
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.BLACK);
            paint.setTextSize(textSize);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF,
                    bold ? Typeface.BOLD : Typeface.NORMAL));
            return paint;
        }
    }

    /**
     * Per-run writing state for the XLSX export: the progress listener, the
     * sheet-count denominator, the header-id cache and the workbook's shared
     * cell styles live here instead of on the service so a failed run can
     * never leak state into a retry (the service itself outlives runs).
     * Analog of {@link ReportWriter}.
     */
    private class SpreadsheetWriter {

        private final ProgressListener mProgressListener;
        /** Items sheet + every contributed sheet; the progress denominator. */
        private final int mTotalSheets;
        /** Cache for the by-name resolution of the :app header string resources. */
        private final Map<String, Integer> mHeaderIdCache = new HashMap<>();
        private final CellStyle mHeaderStyle;
        private final CellStyle mDateStyle;

        private SpreadsheetWriter(ProgressListener progressListener, int totalSheets,
                                  SXSSFWorkbook workbook) {
            mProgressListener = progressListener;
            mTotalSheets = totalSheets;
            mHeaderStyle = createHeaderStyle(workbook);
            mDateStyle = createDateStyle(workbook);
        }

        /**
         * Resolves one contributor header key to its localized :app string. The
         * sheet header strings live in :app resources (feature modules must not
         * depend on :app), so contributors declare the stable resource NAMES and
         * this writer resolves them by name, cached per export run; an
         * unresolved key falls back to the raw name so a rename is visible in the
         * export instead of silently blank.
         */
        private String resolveHeader(String headerKey) {
            Integer id = mHeaderIdCache.get(headerKey);
            if (id == null) {
                id = mAppContext.getResources().getIdentifier(
                        headerKey, "string", mAppContext.getPackageName());
                mHeaderIdCache.put(headerKey, id);
            }
            return id != 0 ? mAppContext.getString(id) : headerKey;
        }

        private void emitSheetProgress(int sheetIndex) {
            progress(mProgressListener,
                    R.string.export_spreadsheet_progress_sheet, sheetIndex, mTotalSheets);
        }

        /**
         * Writes one contributed sheet: localized headers from the declared
         * header keys, then the contributor's (keyset-paged) row writer adapted
         * to the streaming workbook.
         */
        private void writeContributedSheet(SXSSFWorkbook workbook,
                                           ModuleSpreadsheetSheet sheet,
                                           SpreadsheetJoinData joinData, int sheetIndex) {
            emitSheetProgress(sheetIndex);
            Sheet poiSheet = workbook.createSheet(sheet.getName());
            List<String> headerKeys = sheet.getHeaderKeys();
            String[] headers = new String[headerKeys.size()];
            for (int i = 0; i < headerKeys.size(); i++) {
                headers[i] = resolveHeader(headerKeys.get(i));
            }
            int[] columnChars = writeHeaderRow(poiSheet, mHeaderStyle, headers);
            sheet.writeRows(new PoiSheetRowSink(poiSheet, columnChars, mDateStyle), joinData);
            applyColumnWidths(poiSheet, columnChars);
        }

        /**
         * Adapts a contributed row writer to the streaming workbook: creates each
         * physical row and routes the typed cell writes through the shared cell
         * helpers so column-width tracking stays identical to the :app-owned
         * sheets.
         */
        private class PoiSheetRowSink implements ModuleSheetRowSink {

            private final Sheet mSheet;
            private final int[] mColumnChars;
            private final CellStyle mDateStyle;
            private int mRowIndex = 1;

            private PoiSheetRowSink(Sheet sheet, int[] columnChars, CellStyle dateStyle) {
                mSheet = sheet;
                mColumnChars = columnChars;
                mDateStyle = dateStyle;
            }

            @Override
            public ModuleSheetRow nextRow() {
                Row row = mSheet.createRow(mRowIndex++);
                return new PoiSheetRow(row, mColumnChars, mDateStyle);
            }
        }

        private void writeItemsSheet(SXSSFWorkbook workbook, List<ItemState> itemStates,
                                     Map<Long, String> itemTagsMap,
                                     Map<Long, String> itemImagesByItemId, int sheetIndex) {
            emitSheetProgress(sheetIndex);
            Sheet sheet = workbook.createSheet(SHEET_ITEMS);
            int[] columnChars = writeHeaderRow(sheet, mHeaderStyle, new String[]{
                    mAppContext.getString(R.string.header_id),
                    mAppContext.getString(R.string.form_name),
                    mAppContext.getString(R.string.form_amount),
                    mAppContext.getString(R.string.form_min_amount),
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
                columnChars[3] = Math.max(columnChars[3],
                        writeNullableAmountCell(row, 3, item.minAmount));
                columnChars[4] = Math.max(columnChars[4], writeDecimalCell(row, 4, item.price));
                columnChars[5] = Math.max(columnChars[5], writeStringCell(row, 5, item.description));
                columnChars[6] = Math.max(columnChars[6], writeStringCell(row, 6, item.barcode));
                columnChars[7] = Math.max(columnChars[7],
                        writeDateCell(row, 7, item.expiredDateTime, mDateStyle));
                columnChars[8] = Math.max(columnChars[8],
                        writeDateCell(row, 8, item.createdDateTime, mDateStyle));
                columnChars[9] = Math.max(columnChars[9],
                        writeDateCell(row, 9, item.updatedDateTime, mDateStyle));
                columnChars[10] = Math.max(columnChars[10],
                        writeStringCell(row, 10, itemTagsMap.get(item.id)));
                columnChars[11] = Math.max(columnChars[11],
                        writeStringCell(row, 11, itemImagesByItemId.get(item.id)));
            }
            applyColumnWidths(sheet, columnChars);
        }
    }

    private static class PoiSheetRow implements ModuleSheetRow {

        private final Row mRow;
        private final int[] mColumnChars;
        private final CellStyle mDateStyle;

        private PoiSheetRow(Row row, int[] columnChars, CellStyle dateStyle) {
            mRow = row;
            mColumnChars = columnChars;
            mDateStyle = dateStyle;
        }

        @Override
        public void longCell(int column, Long value) {
            mColumnChars[column] = Math.max(mColumnChars[column], writeLongCell(mRow, column, value));
        }

        @Override
        public void stringCell(int column, String value) {
            mColumnChars[column] = Math.max(mColumnChars[column], writeStringCell(mRow, column, value));
        }

        @Override
        public void amountCell(int column, int value) {
            mColumnChars[column] = Math.max(mColumnChars[column], writeAmountCell(mRow, column, value));
        }

        @Override
        public void decimalCell(int column, java.math.BigDecimal value) {
            mColumnChars[column] = Math.max(mColumnChars[column], writeDecimalCell(mRow, column, value));
        }

        @Override
        public void dateCell(int column, java.util.Date value) {
            mColumnChars[column] = Math.max(mColumnChars[column],
                    writeDateCell(mRow, column, value, mDateStyle));
        }
    }

    /**
     * Derived column x positions of the summary table, computed once per
     * export from the measured cell widths (see
     * {@link ReportWriter#computeTableLayout}). Numeric columns store their
     * RIGHT edge (they are right-aligned), text columns their left origin.
     */
    private static class TableLayout {
        float xNo;
        float xName;
        float nameWidth;
        float qtyRight;
        float unitRight;
        float lineRight;
        float xPurchase;
    }
}
