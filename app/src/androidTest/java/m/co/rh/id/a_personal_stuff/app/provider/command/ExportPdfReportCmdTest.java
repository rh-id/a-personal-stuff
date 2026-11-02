package m.co.rh.id.a_personal_stuff.app.provider.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.service.ExportService;
import m.co.rh.id.a_personal_stuff.app.testutil.TestSeeds;
import m.co.rh.id.a_personal_stuff.base.contributor.ItemPdfRecord;
import m.co.rh.id.a_personal_stuff.base.contributor.ModuleContributorRegistry;
import m.co.rh.id.a_personal_stuff.base.contributor.ModulePdfContributor;
import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.base.test.NoOpLogger;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchasePdfContributor;
import m.co.rh.id.a_personal_stuff.item_purchase.room.ItemPurchaseDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Integration tests for {@link ExportPdfReportCmd}. Unlike the XLSX export
 * test (whose image assertions can work on metadata-only rows), this test
 * must seed REAL JPEG files: the whole point of the PDF report is drawing
 * those files, and the PdfRenderer assertions below exist to catch blank
 * image output on older Skia backends.
 */
@RunWith(AndroidJUnit4.class)
public class ExportPdfReportCmdTest {
    /**
     * Threshold for "a photo was actually rendered": a seeded JPEG carries
     * three saturated hues, so its rendered thumbnail yields dozens of
     * distinct chromatic (non-gray) colors, while text anti-aliasing stays
     * achromatic. This is the empirical guard against blank-image output.
     */
    private static final int MIN_CHROMATIC_COLORS_FOR_PHOTO = 10;
    /** Chroma threshold separating a real color from white/black/gray text AA. */
    private static final int CHROMA_THRESHOLD = 16;
    /**
     * Records the stub contributor feeds for ONE item, each with a note so
     * every record renders 2 lines (line + indented note): at 14pt per line
     * this adds ~2800pt of detail-block content (~4 extra pages), so the
     * page-count growth the multi-contributor merge must produce is far
     * above the +2-page assertion margin.
     */
    private static final int STUB_RECORD_COUNT = 100;

    private Context mContext;
    private AppDatabase mAppDb;
    private ItemPurchaseDatabase mPurchaseDb;
    private ExecutorService mExecutorService;
    private ItemFileHelper mItemFileHelper;
    private List<String> mSeededFileNames;
    private Provider mProvider;
    private TestPdfReportProviderModule mTestModule;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mExecutorService = Executors.newFixedThreadPool(2);

        mAppDb = Room.inMemoryDatabaseBuilder(mContext, AppDatabase.class)
                .allowMainThreadQueries().build();

        mPurchaseDb = Room.inMemoryDatabaseBuilder(mContext, ItemPurchaseDatabase.class)
                .allowMainThreadQueries().build();

        mTestModule = new TestPdfReportProviderModule();
        mProvider = Provider.createProvider(mContext, mTestModule);
        // constructing ItemFileHelper starts its async orphan-file cleanup,
        // which deletes image files without a matching DB row; every test
        // therefore seeds the DB rows BEFORE the files, so a listed file
        // always has its row by the time the cleanup checks it
        mItemFileHelper = mProvider.get(ItemFileHelper.class);
        mSeededFileNames = new ArrayList<>();
    }

    @After
    public void tearDown() {
        if (mSeededFileNames != null && mItemFileHelper != null) {
            // deleteItemImage clears both the full-image and thumbnail dir
            for (String fileName : mSeededFileNames) {
                mItemFileHelper.deleteItemImage(fileName);
            }
        }
        if (mAppDb != null) {
            mAppDb.close();
        }
        if (mPurchaseDb != null) {
            mPurchaseDb.close();
        }
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
        }
    }

    @Test
    public void exportEmptyDatabase() throws Exception {
        ExportPdfReportCmd cmd = new ExportPdfReportCmd(mProvider);
        File pdfFile = cmd.execute().blockingGet();
        try {
            assertNotNull(pdfFile);
            assertTrue(pdfFile.exists());
            assertTrue(pdfFile.length() > 0);
            assertPdfMagic(pdfFile);
            withRenderer(pdfFile, renderer -> {
                // cover page + summary table page (header + totals row only)
                assertEquals(2, renderer.getPageCount());
            });
        } finally {
            pdfFile.delete();
        }
    }

    @Test
    public void exportWithFullData() throws Exception {
        Date now = new Date();

        // item 1: priced, tagged, TWO real thumbnail photos, expiry, barcode
        // and a long description (drives the StaticLayout wrap path)
        Item item = TestSeeds.item("Camera", now);
        item.amount = 3;
        item.price = new BigDecimal("29.99");
        item.description = longDescription();
        item.barcode = "4006381333931";
        item.expiredDateTime = new Date(now.getTime() + 2592000000L);
        // TreeSet sorts the tags, so "electronics" is joined before "gear"
        TreeSet<ItemTag> itemTags = new TreeSet<>();
        itemTags.add(TestSeeds.itemTag("gear", now));
        itemTags.add(TestSeeds.itemTag("electronics", now));
        ItemImage img1 = TestSeeds.itemImage("cam1.jpg", now);
        ItemImage img2 = TestSeeds.itemImage("cam2.jpg", now);
        img2.createdDateTime = new Date(now.getTime() + 1000);
        // rows BEFORE files: see the setUp note about ItemFileHelper cleanUp
        long itemId = TestSeeds.insertItemWithRelations(mAppDb, item, itemTags,
                new ArrayList<>(Arrays.asList(img1, img2)));
        seedThumbnailFile("cam1.jpg");
        seedThumbnailFile("cam2.jpg");

        // three purchases, inserted so the LATER date gets the SMALLER id:
        // earliest-selection must pick the earlier-dated row regardless of
        // insertion order, and the undated row must never win
        ItemPurchase laterPurchase = TestSeeds.itemPurchase(itemId,
                new BigDecimal("99.00"), new Date(now.getTime() + 86400000L), now);
        laterPurchase.description = "Replaced broken one";
        // Room does NOT write the auto-generated id back into the inserted
        // entity — ItemPurchaseDao.insert returns the rowId (see how the
        // DAO's own insertItemPurchaseImage assigns it) — so capture the
        // returned ids for the assertions below
        long laterPurchaseId = mPurchaseDb.itemPurchaseDao().insert(laterPurchase);

        ItemPurchase earlierPurchase = TestSeeds.itemPurchase(itemId,
                new BigDecimal("12.50"), now, now);
        earlierPurchase.description = "Bought 3 units";
        earlierPurchase.amount = 3;
        long earlierPurchaseId = mPurchaseDb.itemPurchaseDao().insert(earlierPurchase);

        // the null purchaseDateTime IS the undated-row case under test
        ItemPurchase undatedPurchase = TestSeeds.itemPurchase(itemId,
                new BigDecimal("1.00"), null, now);
        long undatedPurchaseId = mPurchaseDb.itemPurchaseDao().insert(undatedPurchase);

        // earliest-selection (Task 1's query): exactly one row per item and
        // it must be the earlier-dated purchase — the later-dated row was
        // inserted first, so its SMALLER id must not have won
        List<ItemPurchase> firstPurchases =
                mPurchaseDb.itemPurchaseDao().findFirstPurchasesPerItem();
        assertEquals(1, firstPurchases.size());
        assertTrue("seed order wrong: the earlier-dated purchase must have the LARGER id",
                earlierPurchaseId > laterPurchaseId);
        assertEquals(earlierPurchaseId, firstPurchases.get(0).id.longValue());
        assertTrue("the undated purchase must never be selected",
                firstPurchases.get(0).id != undatedPurchaseId);

        // item 2: null price, null expiry, thumbnail MISSING but full image
        // present (exercises the inSampleSize full-image fallback decode)
        Item item2 = TestSeeds.item("Mystery Box", now);
        item2.description = "Kept in storage room B";
        ItemImage fullOnlyImage = TestSeeds.itemImage("mystery_full.jpg", now);
        TestSeeds.insertItemWithRelations(mAppDb, item2, null,
                new ArrayList<>(Arrays.asList(fullOnlyImage)));
        seedFullImageFile("mystery_full.jpg");

        // item 3: zero amount with a zero price is legal (line total 0.00,
        // contributes zero to the totals row)
        Item item3 = TestSeeds.item("Free Sample", now);
        item3.amount = 0;
        item3.price = new BigDecimal("0.00");
        TestSeeds.insertItemWithRelations(mAppDb, item3, null, null);

        File pdfFile = new ExportPdfReportCmd(mProvider).execute().blockingGet();
        try {
            assertPdfMagic(pdfFile);
            withRenderer(pdfFile, renderer -> {
                int pageCount = renderer.getPageCount();
                // cover + summary table + at least one detail page
                assertTrue("cover + table + details expected, got "
                        + pageCount + " pages", pageCount >= 3);
                // photo pages must actually render pixels: the empirical
                // guard against blank-image output on older Skia backends
                int bestChromatic = 0;
                for (int i = 0; i < pageCount; i++) {
                    bestChromatic = Math.max(bestChromatic,
                            countChromaticColors(renderer, i));
                }
                assertTrue("no page rendered a photo (best page had "
                        + bestChromatic + " chromatic colors)",
                        bestChromatic >= MIN_CHROMATIC_COLORS_FOR_PHOTO);
            });
        } finally {
            pdfFile.delete();
        }
    }

    @Test
    public void exportImageBudgetExceededOmitsPhotos() throws Exception {
        Date now = new Date();

        Item item = TestSeeds.item("Bulk Photo Item", now);
        item.price = new BigDecimal("5.00");
        ItemImage img1 = TestSeeds.itemImage("budget1.jpg", now);
        ItemImage img2 = TestSeeds.itemImage("budget2.jpg", now);
        img2.createdDateTime = new Date(now.getTime() + 1000);
        TestSeeds.insertItemWithRelations(mAppDb, item, null,
                new ArrayList<>(Arrays.asList(img1, img2)));
        seedThumbnailFile("budget1.jpg");
        seedThumbnailFile("budget2.jpg");

        // budget 0: every drawable photo position must degrade to the
        // "(photo omitted — image limit reached)" placeholder instead of
        // drawing (or OOMing on) the real images. The budget lives on the
        // ExportService now; the registry resolves it lazily on the first
        // cmd construction, so the override must be set before that.
        mTestModule.setImageBudget(0);
        ExportPdfReportCmd cmd = new ExportPdfReportCmd(mProvider);
        File pdfFile = cmd.execute().blockingGet();
        try {
            assertPdfMagic(pdfFile);
            withRenderer(pdfFile, renderer -> {
                for (int i = 0; i < renderer.getPageCount(); i++) {
                    assertEquals("photos must be omitted with a 0 budget (page "
                            + i + " rendered chromatic pixels)", 0,
                            countChromaticColors(renderer, i));
                }
            });
        } finally {
            pdfFile.delete();
        }
    }

    @Test
    public void exportProgressFlow() throws Exception {
        Date now = new Date();

        Item item = TestSeeds.item("Progress Item", now);
        TestSeeds.insertItemWithRelations(mAppDb, item, null, null);

        List<String> progress = new ArrayList<>();
        ExportPdfReportCmd cmd = new ExportPdfReportCmd(mProvider);
        // Subscribe BEFORE execute(): PublishSubject delivers synchronously to
        // observers on the export's executor thread, and blockingGet's
        // FutureTask completion provides the happens-before edge, so the list
        // is fully populated and visible once blockingGet returns.
        cmd.getProgressFlow().subscribe(progress::add);

        File pdfFile = cmd.execute().blockingGet();
        try {
            // gathering + cover + table + details(0/1) + writing
            assertEquals(5, progress.size());
            assertEquals(mContext.getString(R.string.export_progress_gathering),
                    progress.get(0));
            assertEquals(mContext.getString(R.string.export_pdf_report_progress_cover),
                    progress.get(1));
            assertEquals(mContext.getString(R.string.export_pdf_report_progress_table),
                    progress.get(2));
            assertEquals(mContext.getString(
                    R.string.export_pdf_report_progress_details, 0, 1), progress.get(3));
            assertEquals(mContext.getString(R.string.export_pdf_report_progress_writing),
                    progress.get(4));
        } finally {
            pdfFile.delete();
        }
    }

    /**
     * Multi-contributor survival test: a SECOND PDF contributor (a stub)
     * contributes a summary date and a large record list for the SAME item
     * the real purchase contributor serves. Under the old whole-object
     * {@code putIfAbsent} merge a second contributor's per-item data could
     * never reach the report at all; under the structural merge its records
     * must grow the report measurably past the single-contributor baseline
     * and its extra summary column must not break the table (page count is
     * the deterministic observable — text isn't extractable from PdfRenderer
     * bitmaps). The stub registers directly into the provider's contributor
     * registry (which {@code createReportPdf} re-reads per run, so a
     * post-setUp registration is seen by the next export) — the registry is
     * rebuilt per test, so the stub cannot leak into other tests.
     */
    @Test
    public void secondContributorDataSurvives() throws Exception {
        Date now = new Date();

        Item item = TestSeeds.item("Stub Target", now);
        long itemId = TestSeeds.insertItemWithRelations(mAppDb, item, null, null);

        // baseline: the purchase contributor alone
        int baselinePages;
        File baselinePdf = new ExportPdfReportCmd(mProvider).execute().blockingGet();
        try {
            baselinePages = renderPageCount(baselinePdf);
        } finally {
            baselinePdf.delete();
        }

        // stub: one summary date (distinct from anything the purchase
        // contributor could produce) + MANY noted records for the same item
        Map<Long, Date> stubDates = new HashMap<>();
        stubDates.put(itemId, new Date(now.getTime() - 86400000L));
        List<ItemPdfRecord> stubRecords = new ArrayList<>();
        for (int i = 0; i < STUB_RECORD_COUNT; i++) {
            stubRecords.add(new ItemPdfRecord(new Date(now.getTime() + i * 1000L),
                    new BigDecimal(i + ".50"), "report_record_test_label", "note " + i));
        }
        Map<Long, List<ItemPdfRecord>> stubRecordsByItem = new HashMap<>();
        stubRecordsByItem.put(itemId, stubRecords);

        ModuleContributorRegistry contributorRegistry =
                mProvider.get(ModuleContributorRegistry.class);
        contributorRegistry.register(new StubPdfContributor(stubDates, stubRecordsByItem));

        File pdfFile = new ExportPdfReportCmd(mProvider).execute().blockingGet();
        try {
            assertPdfMagic(pdfFile);
            withRenderer(pdfFile, renderer -> {
                int pageCount = renderer.getPageCount();
                assertTrue("the stub contributor's records must grow the report "
                                + "past the single-contributor baseline (" + baselinePages
                                + " pages); got " + pageCount + " pages",
                        pageCount >= baselinePages + 2);
            });
        } finally {
            pdfFile.delete();
        }
    }

    private int renderPageCount(File pdfFile) throws Exception {
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd)) {
            return renderer.getPageCount();
        }
    }

    /**
     * Test stub implementing the 3-method PDF contract: one extra summary
     * column (its header resolves from the same test string the records use
     * as their label key) plus per-item dates and records supplied by the
     * test.
     */
    private static class StubPdfContributor implements ModulePdfContributor {

        private final Map<Long, Date> mSummaryDates;
        private final Map<Long, List<ItemPdfRecord>> mRecords;

        StubPdfContributor(Map<Long, Date> summaryDates,
                           Map<Long, List<ItemPdfRecord>> records) {
            mSummaryDates = summaryDates;
            mRecords = records;
        }

        @Override
        public String getSummaryColumnKey() {
            return "report_record_test_label";
        }

        @Override
        public Map<Long, Date> getSummaryDates() {
            return mSummaryDates;
        }

        @Override
        public Map<Long, List<ItemPdfRecord>> getRecords() {
            return mRecords;
        }
    }

    /**
     * Seeds one real JPEG under the item-image THUMBNAIL dir with three
     * saturated hues: enough distinct chromatic colors that a rendered page
     * either clearly contains the photo or clearly does not (see
     * {@link #countChromaticColors(PdfRenderer, int)}).
     */
    private void seedThumbnailFile(String fileName) throws IOException {
        File thumbDir = new File(mContext.getFilesDir(),
                Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL);
        thumbDir.mkdirs();
        writeTestJpeg(new File(thumbDir, fileName),
                Color.rgb(200, 40, 40), Color.rgb(40, 60, 200), Color.rgb(250, 210, 40));
        mSeededFileNames.add(fileName);
    }

    /**
     * Seeds one real JPEG under the item-image FULL dir (no thumbnail file):
     * the export must fall back to decoding the full image with
     * inSampleSize when the thumbnail is missing.
     */
    private void seedFullImageFile(String fileName) throws IOException {
        File fullDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_IMAGE);
        fullDir.mkdirs();
        writeTestJpeg(new File(fullDir, fileName),
                Color.rgb(30, 160, 90), Color.rgb(170, 40, 160), Color.rgb(240, 120, 30));
        mSeededFileNames.add(fileName);
    }

    private void writeTestJpeg(File file, int baseColor, int bandColor,
                               int accentColor) throws IOException {
        // same pixel size as the app's own thumbnails (320x180, see
        // ItemFileHelper.createItemImageThumbnail)
        Bitmap bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(baseColor);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(bandColor);
            canvas.drawRect(0, 0, 160, 180, paint);
            paint.setColor(accentColor);
            canvas.drawCircle(240, 90, 45, paint);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
        } finally {
            // test-side bitmap that never entered a PdfDocument: recycling is
            // safe here (the export's no-recycle rule covers decoded report
            // bitmaps, see ExportService.createReportPdf's memory strategy)
            bitmap.recycle();
        }
    }

    private static void assertPdfMagic(File pdfFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(pdfFile)) {
            byte[] header = new byte[4];
            assertEquals("file must start with the %PDF magic", 4, fis.read(header));
            assertEquals("%PDF", new String(header, StandardCharsets.US_ASCII));
        }
    }

    private void withRenderer(File pdfFile, RendererConsumer consumer) throws Exception {
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd)) {
            consumer.accept(renderer);
        }
    }

    private interface RendererConsumer {
        void accept(PdfRenderer renderer) throws Exception;
    }

    /**
     * Renders one page to ARGB_8888 and counts DISTINCT chromatic colors
     * (pixels whose channel spread exceeds {@link #CHROMA_THRESHOLD}).
     * Photos produce many; text/rule anti-aliasing stays achromatic — so the
     * count empirically separates "photo drawn" from "blank placeholder".
     */
    private static int countChromaticColors(PdfRenderer renderer, int pageIndex)
            throws IOException {
        try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
            Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(),
                    Bitmap.Config.ARGB_8888);
            try {
                // the renderer may leave untouched pixels transparent
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return countChromaticColors(bitmap);
            } finally {
                bitmap.recycle();
            }
        }
    }

    private static int countChromaticColors(Bitmap bitmap) {
        Set<Integer> colors = new HashSet<>();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int total = width * height;
        // sample ~100k pixels: far above the photo threshold, far below the
        // full 500k-pixel page scan
        int step = Math.max(1, total / 100_000);
        for (int i = 0; i < total; i += step) {
            int pixel = bitmap.getPixel(i % width, i / width);
            int red = Color.red(pixel);
            int green = Color.green(pixel);
            int blue = Color.blue(pixel);
            int max = Math.max(red, Math.max(green, blue));
            int min = Math.min(red, Math.min(green, blue));
            if (max - min > CHROMA_THRESHOLD && max > CHROMA_THRESHOLD) {
                colors.add(pixel & 0xFFFFFF);
            }
        }
        return colors.size();
    }

    private static String longDescription() {
        char[] chars = new char[600];
        Arrays.fill(chars, 'x');
        return "Serial 12345, storage room A. " + new String(chars);
    }

    private class TestPdfReportProviderModule implements ProviderModule {
        /**
         * Null = the service's default budget; the budget-0 test sets 0
         * before its first cmd construction, which is what triggers the lazy
         * ExportService resolution below.
         */
        private Integer mImageBudget;

        void setImageBudget(Integer imageBudget) {
            mImageBudget = imageBudget;
        }

        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ILogger.class, NoOpLogger::new);
            providerRegistry.register(ExecutorService.class, this::getExecutorService);
            providerRegistry.register(ItemDao.class, this::getItemDao);
            providerRegistry.register(ItemPurchaseDao.class, this::getItemPurchaseDao);
            providerRegistry.register(ModuleContributorRegistry.class,
                    ModuleContributorRegistry::new);
            // the PDF report's per-item data comes from the contributors now
            ModuleContributorRegistry contributorRegistry =
                    provider.get(ModuleContributorRegistry.class);
            contributorRegistry.register(new ItemPurchasePdfContributor(provider));
            providerRegistry.registerLazy(FileHelper.class, () -> new FileHelper(provider));
            // the XLSX test module does not register this (metadata-only image
            // rows suffice there); the PDF export resolves REAL image files
            // through it
            providerRegistry.registerLazy(ItemFileHelper.class,
                    () -> new ItemFileHelper(provider));
            // resolved by ExportPdfReportCmd's constructor; the budget-0 test
            // overrides the image budget via setImageBudget before the first
            // resolution
            providerRegistry.registerLazy(ExportService.class, () -> mImageBudget == null
                    ? new ExportService(provider)
                    : new ExportService(provider, mImageBudget));
        }

        private ExecutorService getExecutorService() {
            return mExecutorService;
        }

        private ItemDao getItemDao() {
            return mAppDb.itemDao();
        }

        private ItemPurchaseDao getItemPurchaseDao() {
            return mPurchaseDb.itemPurchaseDao();
        }
    }
}
