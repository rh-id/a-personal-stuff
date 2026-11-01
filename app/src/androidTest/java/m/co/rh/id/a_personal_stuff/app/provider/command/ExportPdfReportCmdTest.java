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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.service.ExportService;
import m.co.rh.id.a_personal_stuff.base.backup.BackupContributorRegistry;
import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.item_purchase.dao.ItemPurchaseDao;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
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
        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Camera";
        item.amount = 3;
        item.price = new BigDecimal("29.99");
        item.description = longDescription();
        item.barcode = "4006381333931";
        item.expiredDateTime = new Date(now.getTime() + 2592000000L);
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);
        // TreeSet sorts the tags, so "electronics" is joined before "gear"
        TreeSet<ItemTag> itemTags = new TreeSet<>();
        itemTags.add(itemTag("gear", now));
        itemTags.add(itemTag("electronics", now));
        itemState.updateItemTags(itemTags);
        ItemImage img1 = new ItemImage();
        img1.fileName = "cam1.jpg";
        img1.createdDateTime = now;
        ItemImage img2 = new ItemImage();
        img2.fileName = "cam2.jpg";
        img2.createdDateTime = new Date(now.getTime() + 1000);
        itemState.updateItemImages(new ArrayList<>(Arrays.asList(img1, img2)));
        // rows BEFORE files: see the setUp note about ItemFileHelper cleanUp
        mAppDb.itemDao().insertItem(itemState);
        long itemId = itemState.getItemId();
        seedThumbnailFile("cam1.jpg");
        seedThumbnailFile("cam2.jpg");

        // three purchases, inserted so the LATER date gets the SMALLER id:
        // earliest-selection must pick the earlier-dated row regardless of
        // insertion order, and the undated row must never win
        ItemPurchase laterPurchase = new ItemPurchase();
        laterPurchase.itemId = itemId;
        laterPurchase.description = "Replaced broken one";
        laterPurchase.amount = 1;
        laterPurchase.cost = new BigDecimal("99.00");
        laterPurchase.purchaseDateTime = new Date(now.getTime() + 86400000L);
        laterPurchase.createdDateTime = now;
        // Room does NOT write the auto-generated id back into the inserted
        // entity — ItemPurchaseDao.insert returns the rowId (see how the
        // DAO's own insertItemPurchaseImage assigns it) — so capture the
        // returned ids for the assertions below
        long laterPurchaseId = mPurchaseDb.itemPurchaseDao().insert(laterPurchase);

        ItemPurchase earlierPurchase = new ItemPurchase();
        earlierPurchase.itemId = itemId;
        earlierPurchase.description = "Bought 3 units";
        earlierPurchase.amount = 3;
        earlierPurchase.cost = new BigDecimal("12.50");
        earlierPurchase.purchaseDateTime = now;
        earlierPurchase.createdDateTime = now;
        long earlierPurchaseId = mPurchaseDb.itemPurchaseDao().insert(earlierPurchase);

        ItemPurchase undatedPurchase = new ItemPurchase();
        undatedPurchase.itemId = itemId;
        undatedPurchase.amount = 1;
        undatedPurchase.cost = new BigDecimal("1.00");
        undatedPurchase.purchaseDateTime = null;
        undatedPurchase.createdDateTime = now;
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
        ItemState itemState2 = new ItemState();
        Item item2 = new Item();
        item2.name = "Mystery Box";
        item2.amount = 1;
        item2.price = null;
        item2.expiredDateTime = null;
        item2.description = "Kept in storage room B";
        item2.createdDateTime = now;
        item2.updatedDateTime = now;
        ItemImage fullOnlyImage = new ItemImage();
        fullOnlyImage.fileName = "mystery_full.jpg";
        fullOnlyImage.createdDateTime = now;
        itemState2.updateItem(item2);
        itemState2.updateItemImages(new ArrayList<>(Arrays.asList(fullOnlyImage)));
        mAppDb.itemDao().insertItem(itemState2);
        seedFullImageFile("mystery_full.jpg");

        // item 3: zero amount with a zero price is legal (line total 0.00,
        // contributes zero to the totals row)
        ItemState itemState3 = new ItemState();
        Item item3 = new Item();
        item3.name = "Free Sample";
        item3.amount = 0;
        item3.price = new BigDecimal("0.00");
        item3.createdDateTime = now;
        item3.updatedDateTime = now;
        itemState3.updateItem(item3);
        mAppDb.itemDao().insertItem(itemState3);

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

        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Bulk Photo Item";
        item.amount = 1;
        item.price = new BigDecimal("5.00");
        item.createdDateTime = now;
        item.updatedDateTime = now;
        ItemImage img1 = new ItemImage();
        img1.fileName = "budget1.jpg";
        img1.createdDateTime = now;
        ItemImage img2 = new ItemImage();
        img2.fileName = "budget2.jpg";
        img2.createdDateTime = new Date(now.getTime() + 1000);
        itemState.updateItem(item);
        itemState.updateItemImages(new ArrayList<>(Arrays.asList(img1, img2)));
        mAppDb.itemDao().insertItem(itemState);
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

        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Progress Item";
        item.amount = 1;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);
        mAppDb.itemDao().insertItem(itemState);

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

    private static ItemTag itemTag(String tag, Date now) {
        ItemTag itemTag = new ItemTag();
        itemTag.tag = tag;
        itemTag.createdDateTime = now;
        return itemTag;
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
            providerRegistry.register(ILogger.class, this::getLogger);
            providerRegistry.register(ExecutorService.class, this::getExecutorService);
            providerRegistry.register(ItemDao.class, this::getItemDao);
            providerRegistry.register(ItemPurchaseDao.class, this::getItemPurchaseDao);
            providerRegistry.register(BackupContributorRegistry.class,
                    BackupContributorRegistry::new);
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

        private ILogger getLogger() {
            return new TestLogger();
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
