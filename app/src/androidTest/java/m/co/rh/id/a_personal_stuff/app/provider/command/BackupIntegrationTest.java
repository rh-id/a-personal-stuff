package m.co.rh.id.a_personal_stuff.app.provider.command;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.room.Room;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import co.rh.id.lib.concurrent_utils.concurrent.executor.WeightedThreadPool;
import m.co.rh.id.a_personal_stuff.base.constants.Constants;
import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.app.entity.BackupData;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.notifier.ItemChangeNotifier;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.item_maintenance.dao.ItemMaintenanceDao;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceFileHelper;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.notifier.ItemMaintenanceChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_maintenance.room.ItemMaintenanceDatabase;
import m.co.rh.id.a_personal_stuff.item_reminder.dao.ItemReminderDao;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.notifier.ItemReminderChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_reminder.room.ItemReminderDatabase;
import m.co.rh.id.a_personal_stuff.item_usage.dao.ItemUsageDao;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageFileHelper;
import m.co.rh.id.a_personal_stuff.item_usage.provider.notifier.ItemUsageChangeNotifier;
import m.co.rh.id.a_personal_stuff.item_usage.room.ItemUsageDatabase;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BackupIntegrationTest {
    private Context mContext;
    private AppDatabase mAppDb;
    private ItemMaintenanceDatabase mMaintenanceDb;
    private ItemUsageDatabase mUsageDb;
    private ItemReminderDatabase mReminderDb;
    private Provider mProvider;
    private File mTempZipDir;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mAppDb = Room.inMemoryDatabaseBuilder(mContext, AppDatabase.class)
                .allowMainThreadQueries().build();

        mMaintenanceDb = Room.inMemoryDatabaseBuilder(mContext, ItemMaintenanceDatabase.class)
                .allowMainThreadQueries().build();

        mUsageDb = Room.inMemoryDatabaseBuilder(mContext, ItemUsageDatabase.class)
                .allowMainThreadQueries().build();

        mReminderDb = Room.inMemoryDatabaseBuilder(mContext, ItemReminderDatabase.class)
                .allowMainThreadQueries().build();

        mTempZipDir = new File(mContext.getCacheDir(), "test_zips_" + System.currentTimeMillis());
        mTempZipDir.mkdirs();

        try {
            WorkManagerTestInitHelper.initializeTestWorkManager(mContext);
        } catch (IllegalStateException ignored) {
        }

        mProvider = Provider.createProvider(mContext, new TestBackupProviderModule());
    }

    @After
    public void tearDown() {
        deleteRecursive(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_IMAGE));
        deleteRecursive(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE));
        deleteRecursive(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_USAGE_IMAGE));
        deleteRecursive(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL));
        deleteRecursive(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL));
        deleteRecursive(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL));
        deleteRecursive(mTempZipDir);

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
    }

    @Test
    public void exportEmptyDatabase() throws Exception {
        File zipFile = new ExportCmd(mProvider).execute().blockingGet();

        assertNotNull(zipFile);
        assertTrue(zipFile.exists());

        BackupData data = extractJsonFromZip(zipFile);
        assertEquals(1, data.version);
        assertTrue(data.exportedAt > 0);
        assertTrue(data.items.isEmpty());
        assertTrue(data.itemImages.isEmpty());
        assertTrue(data.itemTags.isEmpty());
        assertTrue(data.itemMaintenances.isEmpty());
        assertTrue(data.itemMaintenanceImages.isEmpty());
        assertTrue(data.itemUsages.isEmpty());
        assertTrue(data.itemUsageImages.isEmpty());
        assertTrue(data.itemReminders.isEmpty());

        Set<String> entries = getZipEntryNames(zipFile);
        assertEquals(1, entries.size());
        assertTrue(entries.contains("backup.json"));

        zipFile.delete();
    }

    @Test
    public void exportWithItemsOnly() throws Exception {
        Date now = new Date();

        ItemState itemState1 = new ItemState();
        Item item1 = new Item();
        item1.name = "Item A";
        item1.amount = 5;
        item1.price = new BigDecimal("19.99");
        item1.description = "Test item A";
        item1.barcode = "111222333";
        item1.expiredDateTime = new Date(now.getTime() + 2592000000L);
        item1.createdDateTime = now;
        item1.updatedDateTime = now;
        itemState1.updateItem(item1);

        ItemImage img1 = new ItemImage();
        img1.fileName = "img1.jpg";
        img1.createdDateTime = now;
        ArrayList<ItemImage> images1 = new ArrayList<>();
        images1.add(img1);
        itemState1.updateItemImages(images1);

        ItemTag tag1 = new ItemTag();
        tag1.tag = "electronics";
        tag1.createdDateTime = now;
        TreeSet<ItemTag> tags1 = new TreeSet<>();
        tags1.add(tag1);
        itemState1.updateItemTags(tags1);

        mAppDb.itemDao().insertItem(itemState1);

        ItemState itemState2 = new ItemState();
        Item item2 = new Item();
        item2.name = "Item B";
        item2.amount = 10;
        item2.price = null;
        item2.description = null;
        item2.createdDateTime = now;
        item2.updatedDateTime = now;
        itemState2.updateItem(item2);

        ItemImage img2 = new ItemImage();
        img2.fileName = "img2.jpg";
        img2.createdDateTime = now;
        ArrayList<ItemImage> images2 = new ArrayList<>();
        images2.add(img2);
        itemState2.updateItemImages(images2);

        mAppDb.itemDao().insertItem(itemState2);

        File zipFile = new ExportCmd(mProvider).execute().blockingGet();
        BackupData data = extractJsonFromZip(zipFile);

        assertEquals(2, data.items.size());
        assertEquals(2, data.itemImages.size());
        assertEquals(1, data.itemTags.size());

        assertEquals("Item A", data.items.get(0).name);
        assertEquals(5, data.items.get(0).amount);
        assertEquals(new BigDecimal("19.99"), data.items.get(0).price);
        assertEquals("Test item A", data.items.get(0).description);
        assertEquals("111222333", data.items.get(0).barcode);
        assertNotNull(data.items.get(0).expiredDateTime);
        assertNotNull(data.items.get(0).updatedDateTime);

        assertEquals("Item B", data.items.get(1).name);
        assertEquals(10, data.items.get(1).amount);
        assertNull(data.items.get(1).price);
        assertNull(data.items.get(1).description);
        assertNull(data.items.get(1).barcode);
        assertNull(data.items.get(1).expiredDateTime);

        assertEquals("electronics", data.itemTags.get(0).tag);

        zipFile.delete();
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
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);

        ItemImage itemImg = new ItemImage();
        itemImg.fileName = "item.jpg";
        itemImg.createdDateTime = now;
        ArrayList<ItemImage> itemImages = new ArrayList<>();
        itemImages.add(itemImg);
        itemState.updateItemImages(itemImages);

        ItemTag itemTag = new ItemTag();
        itemTag.tag = "fulltest";
        itemTag.createdDateTime = now;
        TreeSet<ItemTag> itemTags = new TreeSet<>();
        itemTags.add(itemTag);
        itemState.updateItemTags(itemTags);

        mAppDb.itemDao().insertItem(itemState);
        long itemId = itemState.getItemId();

        ItemMaintenance maintenance = new ItemMaintenance();
        maintenance.itemId = itemId;
        maintenance.description = "Oil change";
        maintenance.cost = new BigDecimal("50.00");
        maintenance.maintenanceDateTime = now;
        maintenance.createdDateTime = now;
        long maintId = mMaintenanceDb.itemMaintenanceDao().insert(maintenance);

        ItemMaintenanceImage maintImg = new ItemMaintenanceImage();
        maintImg.itemMaintenanceId = maintId;
        maintImg.fileName = "maint.jpg";
        maintImg.createdDateTime = now;
        mMaintenanceDb.itemMaintenanceDao().insert(maintImg);

        ItemUsage usage = new ItemUsage();
        usage.itemId = itemId;
        usage.description = "Used for project";
        usage.amount = 1;
        usage.createdDateTime = now;
        long usageId = mUsageDb.itemUsageDao().insert(usage);

        ItemUsageImage usageImg = new ItemUsageImage();
        usageImg.itemUsageId = usageId;
        usageImg.fileName = "usage.jpg";
        usageImg.createdDateTime = now;
        mUsageDb.itemUsageDao().insert(usageImg);

        ItemReminder reminder = new ItemReminder();
        reminder.itemId = itemId;
        reminder.taskId = "task-123";
        reminder.reminderDateTime = new Date(now.getTime() + 86400000);
        reminder.message = "Check item status";
        reminder.createdDateTime = now;
        mReminderDb.itemReminderDao().insertItemReminder(reminder);

        File zipFile = new ExportCmd(mProvider).execute().blockingGet();
        BackupData data = extractJsonFromZip(zipFile);

        assertEquals(1, data.items.size());
        assertEquals(1, data.itemImages.size());
        assertEquals(1, data.itemTags.size());
        assertEquals(1, data.itemMaintenances.size());
        assertEquals(1, data.itemMaintenanceImages.size());
        assertEquals(1, data.itemUsages.size());
        assertEquals(1, data.itemUsageImages.size());
        assertEquals(1, data.itemReminders.size());

        assertEquals("Full Item", data.items.get(0).name);
        assertEquals(3, data.items.get(0).amount);
        assertEquals(new BigDecimal("29.99"), data.items.get(0).price);
        assertEquals("Complete test item", data.items.get(0).description);

        assertEquals("item.jpg", data.itemImages.get(0).fileName);
        assertEquals(Long.valueOf(itemId), data.itemImages.get(0).itemId);

        assertEquals("fulltest", data.itemTags.get(0).tag);
        assertEquals(Long.valueOf(itemId), data.itemTags.get(0).itemId);

        assertEquals("Oil change", data.itemMaintenances.get(0).description);
        assertEquals(new BigDecimal("50.00"), data.itemMaintenances.get(0).cost);
        assertEquals(Long.valueOf(itemId), data.itemMaintenances.get(0).itemId);

        assertEquals("maint.jpg", data.itemMaintenanceImages.get(0).fileName);
        assertEquals(maintId, data.itemMaintenanceImages.get(0).itemMaintenanceId.longValue());

        assertEquals("Used for project", data.itemUsages.get(0).description);
        assertEquals(1, data.itemUsages.get(0).amount);
        assertEquals(Long.valueOf(itemId), data.itemUsages.get(0).itemId);

        assertEquals("usage.jpg", data.itemUsageImages.get(0).fileName);
        assertEquals(usageId, data.itemUsageImages.get(0).itemUsageId.longValue());

        assertEquals("Check item status", data.itemReminders.get(0).message);
        assertEquals("task-123", data.itemReminders.get(0).taskId);
        assertEquals(Long.valueOf(itemId), data.itemReminders.get(0).itemId);

        zipFile.delete();
    }

    @Test
    public void exportIncludesReferencedImageFiles() throws Exception {
        Date now = new Date();

        File imageDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_IMAGE);
        imageDir.mkdirs();

        File referencedImageFile = new File(imageDir, "referenced.jpg");
        referencedImageFile.createNewFile();
        FileOutputStream fos = new FileOutputStream(referencedImageFile);
        fos.write("test".getBytes());
        fos.close();

        File thumbnailDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL);
        thumbnailDir.mkdirs();
        File referencedThumbnail = new File(thumbnailDir, "referenced.jpg");
        referencedThumbnail.createNewFile();
        FileOutputStream thumbFos = new FileOutputStream(referencedThumbnail);
        thumbFos.write("thumb".getBytes());
        thumbFos.close();

        File orphanImageFile = new File(imageDir, "orphan.jpg");
        orphanImageFile.createNewFile();
        FileOutputStream orphanFos = new FileOutputStream(orphanImageFile);
        orphanFos.write("orphan".getBytes());
        orphanFos.close();

        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Item With Image";
        item.amount = 1;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);

        ItemImage img = new ItemImage();
        img.fileName = "referenced.jpg";
        img.createdDateTime = now;
        ArrayList<ItemImage> images = new ArrayList<>();
        images.add(img);
        itemState.updateItemImages(images);

        mAppDb.itemDao().insertItem(itemState);

        File zipFile = new ExportCmd(mProvider).execute().blockingGet();
        Set<String> entries = getZipEntryNames(zipFile);

        boolean hasReferencedImage = false;
        boolean hasOrphanImage = false;
        for (String entry : entries) {
            if (entry.startsWith(Constants.FILE_DIR_ITEM_IMAGE) && entry.endsWith("referenced.jpg")) {
                hasReferencedImage = true;
            }
            if (entry.startsWith(Constants.FILE_DIR_ITEM_IMAGE) && entry.endsWith("orphan.jpg")) {
                hasOrphanImage = true;
            }
        }
        assertTrue(hasReferencedImage);
        assertTrue(!hasOrphanImage);

        assertEquals(3, entries.size());
        assertTrue(entries.contains("backup.json"));
        boolean hasThumbnail = false;
        for (String entry : entries) {
            if (entry.startsWith(Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL) && entry.endsWith("referenced.jpg")) {
                hasThumbnail = true;
            }
        }
        assertTrue(hasThumbnail);

        zipFile.delete();
    }

    @Test
    public void exportIncludesMaintenanceAndUsageImages() throws Exception {
        Date now = new Date();

        File maintImageDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE);
        maintImageDir.mkdirs();
        File referencedMaintImage = new File(maintImageDir, "ref_maint.jpg");
        writeBytes(referencedMaintImage, "maint-data".getBytes());

        File maintThumbDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL);
        maintThumbDir.mkdirs();
        File referencedMaintThumb = new File(maintThumbDir, "ref_maint.jpg");
        writeBytes(referencedMaintThumb, "maint-thumb".getBytes());

        File orphanMaintImage = new File(maintImageDir, "orphan_maint.jpg");
        writeBytes(orphanMaintImage, "orphan-maint".getBytes());

        File usageImageDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_USAGE_IMAGE);
        usageImageDir.mkdirs();
        File referencedUsageImage = new File(usageImageDir, "ref_usage.jpg");
        writeBytes(referencedUsageImage, "usage-data".getBytes());

        File usageThumbDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL);
        usageThumbDir.mkdirs();
        File referencedUsageThumb = new File(usageThumbDir, "ref_usage.jpg");
        writeBytes(referencedUsageThumb, "usage-thumb".getBytes());

        File orphanUsageImage = new File(usageImageDir, "orphan_usage.jpg");
        writeBytes(orphanUsageImage, "orphan-usage".getBytes());

        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Filter Test Item";
        item.amount = 1;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);
        mAppDb.itemDao().insertItem(itemState);
        long itemId = itemState.getItemId();

        ItemMaintenance maint = new ItemMaintenance();
        maint.itemId = itemId;
        maint.description = "Filter test maint";
        maint.maintenanceDateTime = now;
        maint.createdDateTime = now;
        long maintId = mMaintenanceDb.itemMaintenanceDao().insert(maint);

        ItemMaintenanceImage maintImg = new ItemMaintenanceImage();
        maintImg.itemMaintenanceId = maintId;
        maintImg.fileName = "ref_maint.jpg";
        maintImg.createdDateTime = now;
        mMaintenanceDb.itemMaintenanceDao().insert(maintImg);

        ItemUsage usage = new ItemUsage();
        usage.itemId = itemId;
        usage.description = "Filter test usage";
        usage.amount = 1;
        usage.createdDateTime = now;
        long usageId = mUsageDb.itemUsageDao().insert(usage);

        ItemUsageImage usageImg = new ItemUsageImage();
        usageImg.itemUsageId = usageId;
        usageImg.fileName = "ref_usage.jpg";
        usageImg.createdDateTime = now;
        mUsageDb.itemUsageDao().insert(usageImg);

        File zipFile = new ExportCmd(mProvider).execute().blockingGet();
        Set<String> entries = getZipEntryNames(zipFile);

        boolean hasRefMaintImage = false;
        boolean hasOrphanMaintImage = false;
        boolean hasRefMaintThumb = false;
        boolean hasRefUsageImage = false;
        boolean hasOrphanUsageImage = false;
        boolean hasRefUsageThumb = false;
        for (String entry : entries) {
            if (entry.startsWith(Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE + "/") && entry.endsWith("ref_maint.jpg") && !entry.contains("thumbnail")) {
                hasRefMaintImage = true;
            }
            if (entry.startsWith(Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE + "/") && entry.endsWith("orphan_maint.jpg") && !entry.contains("thumbnail")) {
                hasOrphanMaintImage = true;
            }
            if (entry.startsWith(Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL + "/") && entry.endsWith("ref_maint.jpg")) {
                hasRefMaintThumb = true;
            }
            if (entry.startsWith(Constants.FILE_DIR_ITEM_USAGE_IMAGE + "/") && entry.endsWith("ref_usage.jpg") && !entry.contains("thumbnail")) {
                hasRefUsageImage = true;
            }
            if (entry.startsWith(Constants.FILE_DIR_ITEM_USAGE_IMAGE + "/") && entry.endsWith("orphan_usage.jpg") && !entry.contains("thumbnail")) {
                hasOrphanUsageImage = true;
            }
            if (entry.startsWith(Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL + "/") && entry.endsWith("ref_usage.jpg")) {
                hasRefUsageThumb = true;
            }
        }
        assertTrue(hasRefMaintImage);
        assertTrue(!hasOrphanMaintImage);
        assertTrue(hasRefMaintThumb);
        assertTrue(hasRefUsageImage);
        assertTrue(!hasOrphanUsageImage);
        assertTrue(hasRefUsageThumb);

        zipFile.delete();
    }

    @Test
    public void importEmptyBackup() throws Exception {
        BackupData data = new BackupData();
        File zipFile = createBackupZip(data);

        int count = new ImportCmd(mProvider).execute(zipFile).blockingGet();

        assertEquals(0, count);
        assertTrue(mAppDb.itemDao().findItemStateWithLimit(Integer.MAX_VALUE, null).isEmpty());
        assertTrue(mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenances().isEmpty());
        assertTrue(mUsageDb.itemUsageDao().findAllItemUsages().isEmpty());
        assertTrue(mReminderDb.itemReminderDao().findAllItemReminders().isEmpty());

        zipFile.delete();
    }

    @Test
    public void importItemsWithImagesAndTags() throws Exception {
        Date now = new Date();

        BackupData data = new BackupData();
        Item item = new Item();
        item.id = 1L;
        item.name = "Import Item";
        item.amount = 7;
        item.price = new BigDecimal("42.50");
        item.description = "Imported description";
        item.barcode = "IMP123456";
        item.expiredDateTime = new Date(now.getTime() + 86400000);
        item.createdDateTime = now;
        item.updatedDateTime = now;
        data.items.add(item);

        ItemImage img1 = new ItemImage();
        img1.id = 1L;
        img1.itemId = 1L;
        img1.fileName = "import1.jpg";
        img1.createdDateTime = now;
        data.itemImages.add(img1);

        ItemImage img2 = new ItemImage();
        img2.id = 2L;
        img2.itemId = 1L;
        img2.fileName = "import2.jpg";
        img2.createdDateTime = now;
        data.itemImages.add(img2);

        ItemTag tag = new ItemTag();
        tag.id = 1L;
        tag.itemId = 1L;
        tag.tag = "imported";
        tag.createdDateTime = now;
        data.itemTags.add(tag);

        File zipFile = createBackupZip(data);

        int count = new ImportCmd(mProvider).execute(zipFile).blockingGet();

        assertEquals(1, count);

        List<ItemState> items = mAppDb.itemDao().findItemStateWithLimit(Integer.MAX_VALUE, null);
        assertEquals(1, items.size());

        ItemState importedItem = items.get(0);
        assertEquals("Import Item", importedItem.getItem().name);
        assertEquals(7, importedItem.getItem().amount);
        assertEquals(new BigDecimal("42.50"), importedItem.getItem().price);
        assertEquals("Imported description", importedItem.getItem().description);
        assertEquals("IMP123456", importedItem.getItem().barcode);
        assertNotNull(importedItem.getItem().expiredDateTime);

        assertEquals(2, importedItem.getItemImages().size());
        assertTrue(importedItem.getItemImages().get(0).fileName.endsWith(".jpg"));
        assertNotEquals("import1.jpg", importedItem.getItemImages().get(0).fileName);
        assertTrue(importedItem.getItemImages().get(1).fileName.endsWith(".jpg"));
        assertNotEquals("import2.jpg", importedItem.getItemImages().get(1).fileName);

        assertEquals(1, importedItem.getItemTags().size());
        assertEquals("imported", importedItem.getItemTags().first().tag);

        zipFile.delete();
    }

    @Test
    public void importFullData() throws Exception {
        Date now = new Date();

        BackupData data = new BackupData();
        Item item = new Item();
        item.id = 1L;
        item.name = "Full Import Item";
        item.amount = 5;
        item.price = new BigDecimal("99.99");
        item.createdDateTime = now;
        item.updatedDateTime = now;
        data.items.add(item);

        ItemImage itemImg = new ItemImage();
        itemImg.id = 1L;
        itemImg.itemId = 1L;
        itemImg.fileName = "item.jpg";
        itemImg.createdDateTime = now;
        data.itemImages.add(itemImg);

        ItemTag itemTag = new ItemTag();
        itemTag.id = 1L;
        itemTag.itemId = 1L;
        itemTag.tag = "fullimport";
        itemTag.createdDateTime = now;
        data.itemTags.add(itemTag);

        ItemMaintenance maintEntry = new ItemMaintenance();
        maintEntry.id = 1L;
        maintEntry.itemId = 1L;
        maintEntry.description = "Repair";
        maintEntry.cost = new BigDecimal("120.00");
        maintEntry.maintenanceDateTime = now;
        maintEntry.createdDateTime = now;
        data.itemMaintenances.add(maintEntry);

        ItemMaintenanceImage maintImgEntry = new ItemMaintenanceImage();
        maintImgEntry.id = 1L;
        maintImgEntry.itemMaintenanceId = 1L;
        maintImgEntry.fileName = "maint.jpg";
        maintImgEntry.createdDateTime = now;
        data.itemMaintenanceImages.add(maintImgEntry);

        ItemUsage usageEntry = new ItemUsage();
        usageEntry.id = 1L;
        usageEntry.itemId = 1L;
        usageEntry.description = "Daily use";
        usageEntry.amount = 1;
        usageEntry.createdDateTime = now;
        data.itemUsages.add(usageEntry);

        ItemUsageImage usageImgEntry = new ItemUsageImage();
        usageImgEntry.id = 1L;
        usageImgEntry.itemUsageId = 1L;
        usageImgEntry.fileName = "usage.jpg";
        usageImgEntry.createdDateTime = now;
        data.itemUsageImages.add(usageImgEntry);

        ItemReminder reminderEntry = new ItemReminder();
        reminderEntry.id = 1L;
        reminderEntry.itemId = 1L;
        reminderEntry.taskId = "old-task";
        reminderEntry.reminderDateTime = new Date(now.getTime() + 86400000);
        reminderEntry.message = "Reminder message";
        reminderEntry.createdDateTime = now;
        data.itemReminders.add(reminderEntry);

        HashMap<String, byte[]> extraFiles = new HashMap<>();
        extraFiles.put(Constants.FILE_DIR_ITEM_IMAGE + "/item.jpg", "item-data".getBytes());
        extraFiles.put(Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL + "/item.jpg", "item-thumb".getBytes());
        extraFiles.put(Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE + "/maint.jpg", "maint-data".getBytes());
        extraFiles.put(Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL + "/maint.jpg", "maint-thumb".getBytes());
        extraFiles.put(Constants.FILE_DIR_ITEM_USAGE_IMAGE + "/usage.jpg", "usage-data".getBytes());
        extraFiles.put(Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL + "/usage.jpg", "usage-thumb".getBytes());

        File zipFile = createBackupZip(data, extraFiles);

        int count = new ImportCmd(mProvider).execute(zipFile).blockingGet();

        assertEquals(1, count);

        List<ItemState> items = mAppDb.itemDao().findItemStateWithLimit(Integer.MAX_VALUE, null);
        assertEquals(1, items.size());
        long newItemId = items.get(0).getItemId();
        assertTrue(newItemId > 0L);

        assertEquals(1, items.get(0).getItemImages().size());
        assertTrue(items.get(0).getItemImages().get(0).fileName.endsWith(".jpg"));
        assertNotEquals("item.jpg", items.get(0).getItemImages().get(0).fileName);
        assertEquals(Long.valueOf(newItemId), items.get(0).getItemImages().get(0).itemId);

        File restoredItemImage = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_IMAGE), items.get(0).getItemImages().get(0).fileName);
        assertTrue(restoredItemImage.exists());

        File restoredItemThumb = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL), items.get(0).getItemImages().get(0).fileName);
        assertTrue(restoredItemThumb.exists());

        assertEquals(1, items.get(0).getItemTags().size());
        assertEquals("fullimport", items.get(0).getItemTags().first().tag);
        assertEquals(Long.valueOf(newItemId), items.get(0).getItemTags().first().itemId);

        List<ItemMaintenance> maintenances = mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenances();
        assertEquals(1, maintenances.size());
        assertEquals(Long.valueOf(newItemId), maintenances.get(0).itemId);
        assertEquals("Repair", maintenances.get(0).description);
        assertEquals(new BigDecimal("120.00"), maintenances.get(0).cost);

        List<ItemMaintenanceImage> maintImages = mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenanceImages();
        assertEquals(1, maintImages.size());
        assertTrue(maintImages.get(0).fileName.endsWith(".jpg"));
        assertNotEquals("maint.jpg", maintImages.get(0).fileName);
        assertEquals(maintenances.get(0).id, maintImages.get(0).itemMaintenanceId);

        File restoredMaintImage = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE), maintImages.get(0).fileName);
        assertTrue(restoredMaintImage.exists());

        File restoredMaintThumb = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL), maintImages.get(0).fileName);
        assertTrue(restoredMaintThumb.exists());

        List<ItemUsage> usages = mUsageDb.itemUsageDao().findAllItemUsages();
        assertEquals(1, usages.size());
        assertEquals(Long.valueOf(newItemId), usages.get(0).itemId);
        assertEquals("Daily use", usages.get(0).description);
        assertEquals(1, usages.get(0).amount);

        List<ItemUsageImage> usageImages = mUsageDb.itemUsageDao().findAllItemUsageImages();
        assertEquals(1, usageImages.size());
        assertTrue(usageImages.get(0).fileName.endsWith(".jpg"));
        assertNotEquals("usage.jpg", usageImages.get(0).fileName);
        assertEquals(usages.get(0).id, usageImages.get(0).itemUsageId);

        File restoredUsageImage = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_USAGE_IMAGE), usageImages.get(0).fileName);
        assertTrue(restoredUsageImage.exists());

        File restoredUsageThumb = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL), usageImages.get(0).fileName);
        assertTrue(restoredUsageThumb.exists());

        List<ItemReminder> reminders = mReminderDb.itemReminderDao().findAllItemReminders();
        assertEquals(1, reminders.size());
        assertEquals(Long.valueOf(newItemId), reminders.get(0).itemId);
        assertEquals("Reminder message", reminders.get(0).message);
        assertTrue(!"old-task".equals(reminders.get(0).taskId));

        zipFile.delete();
    }

    @Test
    public void roundTrip() throws Exception {
        Date now = new Date();

        File itemImageDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_IMAGE);
        itemImageDir.mkdirs();
        File itemImageFile = new File(itemImageDir, "roundtrip.jpg");
        writeBytes(itemImageFile, "roundtrip-item-data".getBytes());

        File itemThumbDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL);
        itemThumbDir.mkdirs();
        File itemThumbFile = new File(itemThumbDir, "roundtrip.jpg");
        writeBytes(itemThumbFile, "roundtrip-item-thumb".getBytes());

        File maintImageDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE);
        maintImageDir.mkdirs();
        File maintImageFile = new File(maintImageDir, "roundtrip_maint.jpg");
        writeBytes(maintImageFile, "roundtrip-maint-data".getBytes());

        File maintThumbDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL);
        maintThumbDir.mkdirs();
        File maintThumbFile = new File(maintThumbDir, "roundtrip_maint.jpg");
        writeBytes(maintThumbFile, "roundtrip-maint-thumb".getBytes());

        File usageImageDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_USAGE_IMAGE);
        usageImageDir.mkdirs();
        File usageImageFile = new File(usageImageDir, "roundtrip_usage.jpg");
        writeBytes(usageImageFile, "roundtrip-usage-data".getBytes());

        File usageThumbDir = new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL);
        usageThumbDir.mkdirs();
        File usageThumbFile = new File(usageThumbDir, "roundtrip_usage.jpg");
        writeBytes(usageThumbFile, "roundtrip-usage-thumb".getBytes());

        ItemState itemState = new ItemState();
        Item item = new Item();
        item.name = "Round Trip Item";
        item.amount = 8;
        item.price = new BigDecimal("123.45");
        item.description = "Round trip test";
        item.barcode = "1234567890";
        item.expiredDateTime = new Date(now.getTime() + 2592000000L);
        item.createdDateTime = now;
        item.updatedDateTime = now;
        itemState.updateItem(item);

        ItemImage itemImg = new ItemImage();
        itemImg.fileName = "roundtrip.jpg";
        itemImg.createdDateTime = now;
        ArrayList<ItemImage> itemImages = new ArrayList<>();
        itemImages.add(itemImg);
        itemState.updateItemImages(itemImages);

        ItemTag itemTag = new ItemTag();
        itemTag.tag = "roundtrip";
        itemTag.createdDateTime = now;
        TreeSet<ItemTag> itemTags = new TreeSet<>();
        itemTags.add(itemTag);
        itemState.updateItemTags(itemTags);

        mAppDb.itemDao().insertItem(itemState);
        long itemId = itemState.getItemId();

        ItemMaintenance maintenance = new ItemMaintenance();
        maintenance.itemId = itemId;
        maintenance.description = "Round trip maintenance";
        maintenance.cost = new BigDecimal("75.00");
        maintenance.maintenanceDateTime = now;
        maintenance.createdDateTime = now;
        long maintId = mMaintenanceDb.itemMaintenanceDao().insert(maintenance);

        ItemMaintenanceImage maintImg = new ItemMaintenanceImage();
        maintImg.itemMaintenanceId = maintId;
        maintImg.fileName = "roundtrip_maint.jpg";
        maintImg.createdDateTime = now;
        mMaintenanceDb.itemMaintenanceDao().insert(maintImg);

        ItemUsage usage = new ItemUsage();
        usage.itemId = itemId;
        usage.description = "Round trip usage";
        usage.amount = 2;
        usage.createdDateTime = now;
        long usageId = mUsageDb.itemUsageDao().insert(usage);

        ItemUsageImage usageImg = new ItemUsageImage();
        usageImg.itemUsageId = usageId;
        usageImg.fileName = "roundtrip_usage.jpg";
        usageImg.createdDateTime = now;
        mUsageDb.itemUsageDao().insert(usageImg);

        ItemReminder reminder = new ItemReminder();
        reminder.itemId = itemId;
        reminder.taskId = "roundtrip-task";
        reminder.reminderDateTime = new Date(now.getTime() + 172800000);
        reminder.message = "Round trip reminder";
        reminder.createdDateTime = now;
        mReminderDb.itemReminderDao().insertItemReminder(reminder);

        File zipFile = new ExportCmd(mProvider).execute().blockingGet();

        mAppDb.close();
        mAppDb = Room.inMemoryDatabaseBuilder(mContext, AppDatabase.class)
                .allowMainThreadQueries().build();

        mMaintenanceDb.close();
        mMaintenanceDb = Room.inMemoryDatabaseBuilder(mContext, ItemMaintenanceDatabase.class)
                .allowMainThreadQueries().build();

        mUsageDb.close();
        mUsageDb = Room.inMemoryDatabaseBuilder(mContext, ItemUsageDatabase.class)
                .allowMainThreadQueries().build();

        mReminderDb.close();
        mReminderDb = Room.inMemoryDatabaseBuilder(mContext, ItemReminderDatabase.class)
                .allowMainThreadQueries().build();

        mProvider = Provider.createProvider(mContext, new TestBackupProviderModule());

        new ImportCmd(mProvider).execute(zipFile).blockingGet();

        List<ItemState> items = mAppDb.itemDao().findItemStateWithLimit(Integer.MAX_VALUE, null);
        assertEquals(1, items.size());
        ItemState restoredItemState = items.get(0);
        Item restoredItem = restoredItemState.getItem();
        assertEquals("Round Trip Item", restoredItem.name);
        assertEquals(8, restoredItem.amount);
        assertEquals(new BigDecimal("123.45"), restoredItem.price);
        assertEquals("Round trip test", restoredItem.description);
        assertEquals("1234567890", restoredItem.barcode);
        assertNotNull(restoredItem.expiredDateTime);

        assertEquals(1, restoredItemState.getItemImages().size());
        assertTrue(restoredItemState.getItemImages().get(0).fileName.endsWith(".jpg"));
        assertNotEquals("roundtrip.jpg", restoredItemState.getItemImages().get(0).fileName);

        File restoredItemImage = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_IMAGE), restoredItemState.getItemImages().get(0).fileName);
        assertTrue(restoredItemImage.exists());

        File restoredItemThumb = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_IMAGE_THUMBNAIL), restoredItemState.getItemImages().get(0).fileName);
        assertTrue(restoredItemThumb.exists());

        assertEquals(1, restoredItemState.getItemTags().size());
        assertEquals("roundtrip", restoredItemState.getItemTags().first().tag);

        List<ItemMaintenance> maintenances = mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenances();
        assertEquals(1, maintenances.size());
        assertEquals("Round trip maintenance", maintenances.get(0).description);
        assertEquals(new BigDecimal("75.00"), maintenances.get(0).cost);

        List<ItemMaintenanceImage> maintImages = mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenanceImages();
        assertEquals(1, maintImages.size());
        assertTrue(maintImages.get(0).fileName.endsWith(".jpg"));
        assertNotEquals("roundtrip_maint.jpg", maintImages.get(0).fileName);
        File restoredMaintImage = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE), maintImages.get(0).fileName);
        assertTrue(restoredMaintImage.exists());
        File restoredMaintThumb = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_MAINTENANCE_IMAGE_THUMBNAIL), maintImages.get(0).fileName);
        assertTrue(restoredMaintThumb.exists());

        List<ItemUsage> usages = mUsageDb.itemUsageDao().findAllItemUsages();
        assertEquals(1, usages.size());
        assertEquals("Round trip usage", usages.get(0).description);
        assertEquals(2, usages.get(0).amount);

        List<ItemUsageImage> usageImages = mUsageDb.itemUsageDao().findAllItemUsageImages();
        assertEquals(1, usageImages.size());
        assertTrue(usageImages.get(0).fileName.endsWith(".jpg"));
        assertNotEquals("roundtrip_usage.jpg", usageImages.get(0).fileName);
        File restoredUsageImage = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_USAGE_IMAGE), usageImages.get(0).fileName);
        assertTrue(restoredUsageImage.exists());
        File restoredUsageThumb = new File(new File(mContext.getFilesDir(), Constants.FILE_DIR_ITEM_USAGE_IMAGE_THUMBNAIL), usageImages.get(0).fileName);
        assertTrue(restoredUsageThumb.exists());

        List<ItemReminder> reminders = mReminderDb.itemReminderDao().findAllItemReminders();
        assertEquals(1, reminders.size());
        assertEquals("Round trip reminder", reminders.get(0).message);

        zipFile.delete();
    }

    @Test
    public void importSameBackupTwice() throws Exception {
        Date now = new Date();

        BackupData data = new BackupData();
        Item item = new Item();
        item.id = 1L;
        item.name = "Duplicate Item";
        item.amount = 3;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        data.items.add(item);

        File zipFile = createBackupZip(data);

        new ImportCmd(mProvider).execute(zipFile).blockingGet();
        int count2 = new ImportCmd(mProvider).execute(zipFile).blockingGet();

        assertEquals(1, count2);
        List<ItemState> items = mAppDb.itemDao().findItemStateWithLimit(Integer.MAX_VALUE, null);
        assertEquals(2, items.size());

        zipFile.delete();
    }

    @Test
    public void importRemapsIds() throws Exception {
        Date now = new Date();

        BackupData data = new BackupData();
        Item item = new Item();
        item.id = 999L;
        item.name = "Old ID Item";
        item.amount = 1;
        item.createdDateTime = now;
        data.items.add(item);

        ItemImage img = new ItemImage();
        img.id = 888L;
        img.itemId = 999L;
        img.fileName = "img.jpg";
        img.createdDateTime = now;
        data.itemImages.add(img);

        File zipFile = createBackupZip(data);

        new ImportCmd(mProvider).execute(zipFile).blockingGet();

        List<ItemState> items = mAppDb.itemDao().findItemStateWithLimit(Integer.MAX_VALUE, null);
        assertEquals(1, items.size());
        long newItemId = items.get(0).getItemId();
        assertTrue(newItemId != 999L);
        assertEquals(1, items.get(0).getItemImages().size());
        assertTrue(items.get(0).getItemImages().get(0).id != 888L);
        assertEquals(Long.valueOf(newItemId), items.get(0).getItemImages().get(0).itemId);

        zipFile.delete();
    }

    @Test
    public void importWithNullFileName() throws Exception {
        Date now = new Date();

        BackupData data = new BackupData();
        Item item = new Item();
        item.id = 1L;
        item.name = "No Image Item";
        item.amount = 1;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        data.items.add(item);

        ItemImage img = new ItemImage();
        img.id = 1L;
        img.itemId = 1L;
        img.fileName = null;
        img.createdDateTime = now;
        data.itemImages.add(img);

        ItemMaintenance maint = new ItemMaintenance();
        maint.id = 1L;
        maint.itemId = 1L;
        maint.description = "No image maint";
        maint.cost = new BigDecimal("10.00");
        maint.maintenanceDateTime = now;
        maint.createdDateTime = now;
        data.itemMaintenances.add(maint);

        ItemMaintenanceImage maintImg = new ItemMaintenanceImage();
        maintImg.id = 1L;
        maintImg.itemMaintenanceId = 1L;
        maintImg.fileName = null;
        maintImg.createdDateTime = now;
        data.itemMaintenanceImages.add(maintImg);

        File zipFile = createBackupZip(data);

        int count = new ImportCmd(mProvider).execute(zipFile).blockingGet();

        assertEquals(1, count);

        List<ItemState> items = mAppDb.itemDao().findItemStateWithLimit(Integer.MAX_VALUE, null);
        assertEquals(1, items.size());
        assertEquals(1, items.get(0).getItemImages().size());
        assertNull(items.get(0).getItemImages().get(0).fileName);

        List<ItemMaintenance> maintenances = mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenances();
        assertEquals(1, maintenances.size());

        List<ItemMaintenanceImage> maintImages = mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenanceImages();
        assertEquals(1, maintImages.size());
        assertNull(maintImages.get(0).fileName);

        zipFile.delete();
    }

    @Test
    public void importGeneratesNewTaskIds() throws Exception {
        Date now = new Date();

        BackupData data = new BackupData();
        Item item = new Item();
        item.id = 1L;
        item.name = "Reminder Item";
        item.amount = 1;
        item.createdDateTime = now;
        data.items.add(item);

        ItemReminder reminder1 = new ItemReminder();
        reminder1.id = 1L;
        reminder1.itemId = 1L;
        reminder1.taskId = "original-task-1";
        reminder1.reminderDateTime = new Date(now.getTime() + 86400000);
        reminder1.message = "Reminder 1";
        reminder1.createdDateTime = now;
        data.itemReminders.add(reminder1);

        ItemReminder reminder2 = new ItemReminder();
        reminder2.id = 2L;
        reminder2.itemId = 1L;
        reminder2.taskId = "original-task-2";
        reminder2.reminderDateTime = new Date(now.getTime() + 172800000);
        reminder2.message = "Reminder 2";
        reminder2.createdDateTime = now;
        data.itemReminders.add(reminder2);

        File zipFile = createBackupZip(data);

        new ImportCmd(mProvider).execute(zipFile).blockingGet();

        List<ItemReminder> reminders = mReminderDb.itemReminderDao().findAllItemReminders();
        assertEquals(2, reminders.size());
        for (ItemReminder r : reminders) {
            assertTrue(!"original-task-1".equals(r.taskId));
            assertTrue(!"original-task-2".equals(r.taskId));
        }
        assertTrue(!reminders.get(0).taskId.equals(reminders.get(1).taskId));

        zipFile.delete();
    }

    @Test
    public void importSkipsOrphans() throws Exception {
        Date now = new Date();

        BackupData data = new BackupData();

        ItemMaintenance orphanMaint = new ItemMaintenance();
        orphanMaint.id = 1L;
        orphanMaint.itemId = 999L;
        orphanMaint.description = "Orphan maintenance";
        orphanMaint.cost = new BigDecimal("10.00");
        orphanMaint.maintenanceDateTime = now;
        orphanMaint.createdDateTime = now;
        data.itemMaintenances.add(orphanMaint);

        ItemMaintenanceImage orphanMaintImg = new ItemMaintenanceImage();
        orphanMaintImg.id = 1L;
        orphanMaintImg.itemMaintenanceId = 1L;
        orphanMaintImg.fileName = "orphan_maint.jpg";
        orphanMaintImg.createdDateTime = now;
        data.itemMaintenanceImages.add(orphanMaintImg);

        ItemUsage orphanUsage = new ItemUsage();
        orphanUsage.id = 1L;
        orphanUsage.itemId = 999L;
        orphanUsage.description = "Orphan usage";
        orphanUsage.amount = 1;
        orphanUsage.createdDateTime = now;
        data.itemUsages.add(orphanUsage);

        ItemUsageImage orphanUsageImg = new ItemUsageImage();
        orphanUsageImg.id = 1L;
        orphanUsageImg.itemUsageId = 1L;
        orphanUsageImg.fileName = "orphan_usage.jpg";
        orphanUsageImg.createdDateTime = now;
        data.itemUsageImages.add(orphanUsageImg);

        ItemReminder orphanReminder = new ItemReminder();
        orphanReminder.id = 1L;
        orphanReminder.itemId = 999L;
        orphanReminder.taskId = "orphan-task";
        orphanReminder.reminderDateTime = new Date(now.getTime() + 86400000);
        orphanReminder.message = "Orphan reminder";
        orphanReminder.createdDateTime = now;
        data.itemReminders.add(orphanReminder);

        File zipFile = createBackupZip(data);

        int count = new ImportCmd(mProvider).execute(zipFile).blockingGet();

        assertEquals(0, count);
        assertTrue(mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenances().isEmpty());
        assertTrue(mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenanceImages().isEmpty());
        assertTrue(mUsageDb.itemUsageDao().findAllItemUsages().isEmpty());
        assertTrue(mUsageDb.itemUsageDao().findAllItemUsageImages().isEmpty());
        assertTrue(mReminderDb.itemReminderDao().findAllItemReminders().isEmpty());

        zipFile.delete();
    }

    @Test
    public void importSkipsOrphanImages() throws Exception {
        Date now = new Date();

        BackupData data = new BackupData();

        Item item = new Item();
        item.id = 1L;
        item.name = "Parent Item";
        item.amount = 1;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        data.items.add(item);

        ItemMaintenance orphanMaint = new ItemMaintenance();
        orphanMaint.id = 99L;
        orphanMaint.itemId = 999L;
        orphanMaint.description = "Orphan";
        orphanMaint.cost = new BigDecimal("10.00");
        orphanMaint.maintenanceDateTime = now;
        orphanMaint.createdDateTime = now;
        data.itemMaintenances.add(orphanMaint);

        ItemMaintenanceImage orphanMaintImg = new ItemMaintenanceImage();
        orphanMaintImg.id = 1L;
        orphanMaintImg.itemMaintenanceId = 99L;
        orphanMaintImg.fileName = "orphan_maint.jpg";
        orphanMaintImg.createdDateTime = now;
        data.itemMaintenanceImages.add(orphanMaintImg);

        ItemUsage orphanUsage = new ItemUsage();
        orphanUsage.id = 99L;
        orphanUsage.itemId = 999L;
        orphanUsage.description = "Orphan usage";
        orphanUsage.amount = 1;
        orphanUsage.createdDateTime = now;
        data.itemUsages.add(orphanUsage);

        ItemUsageImage orphanUsageImg = new ItemUsageImage();
        orphanUsageImg.id = 1L;
        orphanUsageImg.itemUsageId = 99L;
        orphanUsageImg.fileName = "orphan_usage.jpg";
        orphanUsageImg.createdDateTime = now;
        data.itemUsageImages.add(orphanUsageImg);

        File zipFile = createBackupZip(data);

        int count = new ImportCmd(mProvider).execute(zipFile).blockingGet();

        assertEquals(1, count);

        List<ItemMaintenance> maintList = mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenances();
        assertTrue(maintList.isEmpty());
        List<ItemMaintenanceImage> maintImgList = mMaintenanceDb.itemMaintenanceDao().findAllItemMaintenanceImages();
        assertTrue(maintImgList.isEmpty());

        List<ItemUsage> usageList = mUsageDb.itemUsageDao().findAllItemUsages();
        assertTrue(usageList.isEmpty());
        List<ItemUsageImage> usageImgList = mUsageDb.itemUsageDao().findAllItemUsageImages();
        assertTrue(usageImgList.isEmpty());

        zipFile.delete();
    }

    @Test
    public void backupDataJsonRoundTrip() throws Exception {
        Date now = new Date();

        BackupData original = new BackupData();
        original.version = 1;
        original.exportedAt = now.getTime();

        Item item = new Item();
        item.id = 1L;
        item.name = "Test Item";
        item.amount = 10;
        item.price = new BigDecimal("99.99");
        item.description = "Test description";
        item.barcode = "9876543210";
        item.expiredDateTime = new Date(now.getTime() + 2592000000L);
        item.createdDateTime = now;
        item.updatedDateTime = now;
        original.items.add(item);

        ItemImage img = new ItemImage();
        img.id = 1L;
        img.itemId = 1L;
        img.fileName = "test.jpg";
        img.createdDateTime = now;
        original.itemImages.add(img);

        ItemTag tag = new ItemTag();
        tag.id = 1L;
        tag.itemId = 1L;
        tag.tag = "testtag";
        tag.createdDateTime = now;
        original.itemTags.add(tag);

        ItemMaintenance maint = new ItemMaintenance();
        maint.id = 1L;
        maint.itemId = 1L;
        maint.description = "Test maintenance";
        maint.cost = new BigDecimal("50.00");
        maint.maintenanceDateTime = now;
        maint.createdDateTime = now;
        original.itemMaintenances.add(maint);

        ItemMaintenanceImage maintImg = new ItemMaintenanceImage();
        maintImg.id = 1L;
        maintImg.itemMaintenanceId = 1L;
        maintImg.fileName = "maint.jpg";
        maintImg.createdDateTime = now;
        original.itemMaintenanceImages.add(maintImg);

        ItemUsage usage = new ItemUsage();
        usage.id = 1L;
        usage.itemId = 1L;
        usage.description = "Test usage";
        usage.amount = 1;
        usage.createdDateTime = now;
        original.itemUsages.add(usage);

        ItemUsageImage usageImg = new ItemUsageImage();
        usageImg.id = 1L;
        usageImg.itemUsageId = 1L;
        usageImg.fileName = "usage.jpg";
        usageImg.createdDateTime = now;
        original.itemUsageImages.add(usageImg);

        ItemReminder reminder = new ItemReminder();
        reminder.id = 1L;
        reminder.itemId = 1L;
        reminder.taskId = "test-task";
        reminder.reminderDateTime = new Date(now.getTime() + 86400000);
        reminder.message = "Test reminder";
        reminder.createdDateTime = now;
        original.itemReminders.add(reminder);

        JSONObject json = original.toJson();
        BackupData restored = BackupData.fromJson(json);

        assertEquals(original.version, restored.version);
        assertEquals(original.exportedAt, restored.exportedAt);

        assertEquals(1, restored.items.size());
        assertEquals(Long.valueOf(1L), restored.items.get(0).id);
        assertEquals("Test Item", restored.items.get(0).name);
        assertEquals(10, restored.items.get(0).amount);
        assertEquals(new BigDecimal("99.99"), restored.items.get(0).price);
        assertEquals("Test description", restored.items.get(0).description);
        assertEquals("9876543210", restored.items.get(0).barcode);
        assertNotNull(restored.items.get(0).expiredDateTime);
        assertEquals(item.expiredDateTime.getTime(), restored.items.get(0).expiredDateTime.getTime());
        assertNotNull(restored.items.get(0).createdDateTime);
        assertEquals(now.getTime(), restored.items.get(0).createdDateTime.getTime());
        assertNotNull(restored.items.get(0).updatedDateTime);
        assertEquals(now.getTime(), restored.items.get(0).updatedDateTime.getTime());

        assertEquals(1, restored.itemImages.size());
        assertEquals(Long.valueOf(1L), restored.itemImages.get(0).id);
        assertEquals(Long.valueOf(1L), restored.itemImages.get(0).itemId);
        assertEquals("test.jpg", restored.itemImages.get(0).fileName);
        assertNotNull(restored.itemImages.get(0).createdDateTime);

        assertEquals(1, restored.itemTags.size());
        assertEquals(Long.valueOf(1L), restored.itemTags.get(0).id);
        assertEquals(Long.valueOf(1L), restored.itemTags.get(0).itemId);
        assertEquals("testtag", restored.itemTags.get(0).tag);
        assertNotNull(restored.itemTags.get(0).createdDateTime);

        assertEquals(1, restored.itemMaintenances.size());
        assertEquals(Long.valueOf(1L), restored.itemMaintenances.get(0).id);
        assertEquals(Long.valueOf(1L), restored.itemMaintenances.get(0).itemId);
        assertEquals("Test maintenance", restored.itemMaintenances.get(0).description);
        assertEquals(new BigDecimal("50.00"), restored.itemMaintenances.get(0).cost);
        assertNotNull(restored.itemMaintenances.get(0).maintenanceDateTime);
        assertEquals(now.getTime(), restored.itemMaintenances.get(0).maintenanceDateTime.getTime());

        assertEquals(1, restored.itemMaintenanceImages.size());
        assertEquals(Long.valueOf(1L), restored.itemMaintenanceImages.get(0).id);
        assertEquals(Long.valueOf(1L), restored.itemMaintenanceImages.get(0).itemMaintenanceId);
        assertEquals("maint.jpg", restored.itemMaintenanceImages.get(0).fileName);

        assertEquals(1, restored.itemUsages.size());
        assertEquals(Long.valueOf(1L), restored.itemUsages.get(0).id);
        assertEquals(Long.valueOf(1L), restored.itemUsages.get(0).itemId);
        assertEquals("Test usage", restored.itemUsages.get(0).description);
        assertEquals(1, restored.itemUsages.get(0).amount);

        assertEquals(1, restored.itemUsageImages.size());
        assertEquals(Long.valueOf(1L), restored.itemUsageImages.get(0).id);
        assertEquals(Long.valueOf(1L), restored.itemUsageImages.get(0).itemUsageId);
        assertEquals("usage.jpg", restored.itemUsageImages.get(0).fileName);

        assertEquals(1, restored.itemReminders.size());
        assertEquals(Long.valueOf(1L), restored.itemReminders.get(0).id);
        assertEquals(Long.valueOf(1L), restored.itemReminders.get(0).itemId);
        assertEquals("test-task", restored.itemReminders.get(0).taskId);
        assertEquals("Test reminder", restored.itemReminders.get(0).message);
        assertNotNull(restored.itemReminders.get(0).reminderDateTime);
        assertEquals(now.getTime() + 86400000, restored.itemReminders.get(0).reminderDateTime.getTime());
    }

    private File createBackupZip(BackupData data) throws Exception {
        File zipFile = new File(mTempZipDir, "test_backup_" + System.currentTimeMillis() + ".aps_backup");
        FileOutputStream fos = new FileOutputStream(zipFile);
        ZipOutputStream zos = new ZipOutputStream(fos);
        try {
            ZipEntry jsonEntry = new ZipEntry("backup.json");
            zos.putNextEntry(jsonEntry);
            byte[] jsonBytes = data.toJson().toString().getBytes(StandardCharsets.UTF_8);
            zos.write(jsonBytes);
            zos.closeEntry();
        } finally {
            zos.close();
            fos.close();
        }
        return zipFile;
    }

    private File createBackupZip(BackupData data, Map<String, byte[]> extraFiles) throws Exception {
        File zipFile = new File(mTempZipDir, "test_backup_" + System.currentTimeMillis() + ".aps_backup");
        FileOutputStream fos = new FileOutputStream(zipFile);
        ZipOutputStream zos = new ZipOutputStream(fos);
        try {
            ZipEntry jsonEntry = new ZipEntry("backup.json");
            zos.putNextEntry(jsonEntry);
            byte[] jsonBytes = data.toJson().toString().getBytes(StandardCharsets.UTF_8);
            zos.write(jsonBytes);
            zos.closeEntry();
            for (Map.Entry<String, byte[]> entry : extraFiles.entrySet()) {
                ZipEntry fileEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(fileEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        } finally {
            zos.close();
        }
        return zipFile;
    }

    private BackupData extractJsonFromZip(File zipFile) throws Exception {
        FileInputStream fis = new FileInputStream(zipFile);
        ZipInputStream zis = new ZipInputStream(fis);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if ("backup.json".equals(entry.getName())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(zis, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                zis.closeEntry();
                zis.close();
                return BackupData.fromJson(new JSONObject(sb.toString()));
            }
            zis.closeEntry();
        }
        zis.close();
        fis.close();
        throw new Exception("backup.json not found in zip");
    }

    private Set<String> getZipEntryNames(File zipFile) throws Exception {
        Set<String> entries = new HashSet<>();
        FileInputStream fis = new FileInputStream(zipFile);
        ZipInputStream zis = new ZipInputStream(fis);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            entries.add(entry.getName());
            zis.closeEntry();
        }
        zis.close();
        fis.close();
        return entries;
    }

    private void writeBytes(File file, byte[] data) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write(data);
        } finally {
            fos.close();
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private class TestBackupProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, this::getExecutorService);
            providerRegistry.register(ILogger.class, this::getLogger);
            providerRegistry.register(ItemDao.class, this::getItemDao);
            providerRegistry.register(ItemMaintenanceDao.class, this::getItemMaintenanceDao);
            providerRegistry.register(ItemUsageDao.class, this::getItemUsageDao);
            providerRegistry.register(ItemReminderDao.class, this::getItemReminderDao);
            providerRegistry.register(ItemChangeNotifier.class, this::getItemChangeNotifier);
            providerRegistry.register(ItemMaintenanceChangeNotifier.class, this::getItemMaintenanceChangeNotifier);
            providerRegistry.register(ItemUsageChangeNotifier.class, this::getItemUsageChangeNotifier);
            providerRegistry.register(ItemReminderChangeNotifier.class, this::getItemReminderChangeNotifier);
            providerRegistry.register(WorkManager.class, this::getWorkManager);
            providerRegistry.registerLazy(FileHelper.class, () -> new FileHelper(provider));
            providerRegistry.registerLazy(ItemFileHelper.class, () -> new ItemFileHelper(provider));
            providerRegistry.registerLazy(ItemMaintenanceFileHelper.class, () -> new ItemMaintenanceFileHelper(provider));
            providerRegistry.registerLazy(ItemUsageFileHelper.class, () -> new ItemUsageFileHelper(provider));
        }

        private ExecutorService getExecutorService() {
            // thread pool to be used throughout this app lifecycle
            WeightedThreadPool weightedThreadPool = new WeightedThreadPool();
            weightedThreadPool.setMaxWeight(5);
            return weightedThreadPool;
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

        private ItemChangeNotifier getItemChangeNotifier() {
            return new ItemChangeNotifier();
        }

        private ItemMaintenanceChangeNotifier getItemMaintenanceChangeNotifier() {
            return new ItemMaintenanceChangeNotifier();
        }

        private ItemUsageChangeNotifier getItemUsageChangeNotifier() {
            return new ItemUsageChangeNotifier();
        }

        private ItemReminderChangeNotifier getItemReminderChangeNotifier() {
            return new ItemReminderChangeNotifier();
        }

        private WorkManager getWorkManager() {
            return WorkManager.getInstance(mContext);
        }
    }

    private class TestLogger implements ILogger {
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