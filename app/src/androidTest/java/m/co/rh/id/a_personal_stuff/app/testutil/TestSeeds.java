package m.co.rh.id.a_personal_stuff.app.testutil;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import m.co.rh.id.a_personal_stuff.base.dao.ItemDao;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.room.AppDatabase;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklist;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLending;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchase;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;

/**
 * Shared entity seed factory for the export/backup integration tests
 * ({@code ExportPdfReportCmdTest}, {@code ExportSpreadsheetCmdTest},
 * {@code BackupIntegrationTest}). Exists to replace the per-file duplicated
 * inline seed boilerplate ({@code new X(); x.field = ...; dao.insert(x)})
 * with one place that carries the common defaults; tests keep full control
 * by mutating the returned entity (e.g. {@code item.amount = 3}) before
 * inserting. Timestamps follow the tests' {@code new Date(now.getTime()
 * + offsetMillis)} convention: every factory takes the test's shared
 * {@code now} and stamps {@code createdDateTime} with it.
 */
public final class TestSeeds {

    private TestSeeds() {
    }

    /**
     * Minimal item: amount 1, no price/description/barcode/expiry/minAmount;
     * created = updated = now. Override fields on the returned entity as needed.
     */
    public static Item item(String name, Date now) {
        Item item = new Item();
        item.name = name;
        item.amount = 1;
        item.createdDateTime = now;
        item.updatedDateTime = now;
        return item;
    }

    /** Item tag with the given text and createdDateTime = now. */
    public static ItemTag itemTag(String tag, Date now) {
        ItemTag itemTag = new ItemTag();
        itemTag.tag = tag;
        itemTag.createdDateTime = now;
        return itemTag;
    }

    /** Item image metadata row with the given file name and createdDateTime = now. */
    public static ItemImage itemImage(String fileName, Date now) {
        ItemImage itemImage = new ItemImage();
        itemImage.fileName = fileName;
        itemImage.createdDateTime = now;
        return itemImage;
    }

    /**
     * Purchase of amount 1 with no description and createdDateTime = now;
     * {@code purchaseDateTime} may be null (the undated-purchase case).
     */
    public static ItemPurchase itemPurchase(long itemId, BigDecimal cost,
                                            Date purchaseDateTime, Date now) {
        ItemPurchase itemPurchase = new ItemPurchase();
        itemPurchase.itemId = itemId;
        itemPurchase.amount = 1;
        itemPurchase.cost = cost;
        // explicit assignment so null (undated) overrides the constructor's
        // "now" default
        itemPurchase.purchaseDateTime = purchaseDateTime;
        itemPurchase.createdDateTime = now;
        return itemPurchase;
    }

    /** Usage of amount 1 with the given description; usage = created = now. */
    public static ItemUsage itemUsage(long itemId, String description, Date now) {
        ItemUsage itemUsage = new ItemUsage();
        itemUsage.itemId = itemId;
        itemUsage.description = description;
        itemUsage.amount = 1;
        itemUsage.usageDateTime = now;
        itemUsage.createdDateTime = now;
        return itemUsage;
    }

    /**
     * Maintenance with the given description and cost (cost may be null);
     * maintenance = created = now.
     */
    public static ItemMaintenance itemMaintenance(long itemId, String description,
                                                  BigDecimal cost, Date now) {
        ItemMaintenance itemMaintenance = new ItemMaintenance();
        itemMaintenance.itemId = itemId;
        itemMaintenance.description = description;
        itemMaintenance.cost = cost;
        itemMaintenance.maintenanceDateTime = now;
        itemMaintenance.createdDateTime = now;
        return itemMaintenance;
    }

    /**
     * Reminder with the given pinned taskId (import tests assert it gets
     * regenerated), message and reminderDateTime; created = now.
     */
    public static ItemReminder itemReminder(long itemId, String taskId, String message,
                                            Date reminderDateTime, Date now) {
        ItemReminder itemReminder = new ItemReminder();
        itemReminder.itemId = itemId;
        itemReminder.taskId = taskId;
        itemReminder.reminderDateTime = reminderDateTime;
        itemReminder.message = message;
        itemReminder.createdDateTime = now;
        return itemReminder;
    }

    /**
     * Lending of the given borrower with no due/returned date and no note;
     * lend = now. Override fields on the returned entity as needed.
     */
    public static ItemLending itemLending(long itemId, String borrowerName, Date now) {
        ItemLending itemLending = new ItemLending();
        itemLending.itemId = itemId;
        itemLending.borrowerName = borrowerName;
        itemLending.lendDateTime = now;
        return itemLending;
    }

    /** Checklist with the given title, no description; created = updated = now. */
    public static ItemChecklist itemChecklist(String title, Date now) {
        ItemChecklist itemChecklist = new ItemChecklist();
        itemChecklist.title = title;
        itemChecklist.createdDateTime = now;
        itemChecklist.updatedDateTime = now;
        return itemChecklist;
    }

    /**
     * Checklist item linking {@code itemId} into {@code itemChecklistId};
     * {@code checkedDateTime} may be null (unchecked), created = now.
     */
    public static ItemChecklistItem itemChecklistItem(long itemChecklistId, long itemId,
                                                      Date checkedDateTime, Date now) {
        ItemChecklistItem itemChecklistItem = new ItemChecklistItem();
        itemChecklistItem.itemChecklistId = itemChecklistId;
        itemChecklistItem.itemId = itemId;
        itemChecklistItem.checkedDateTime = checkedDateTime;
        itemChecklistItem.createdDateTime = now;
        return itemChecklistItem;
    }

    /**
     * Inserts the item with its relations through {@link ItemDao#insertItem(ItemState)}
     * and returns the generated item id. {@code tags} (kept sorted, ItemState
     * stores them in a TreeSet) and {@code images} may be null when the seed
     * has none.
     */
    public static long insertItemWithRelations(AppDatabase db, Item item,
                                               Set<ItemTag> tags, List<ItemImage> images) {
        ItemState itemState = new ItemState();
        itemState.updateItem(item);
        if (tags != null) {
            // updateItemTags copies into a TreeSet, so any Set works here
            itemState.updateItemTags(new TreeSet<>(tags));
        }
        if (images != null) {
            itemState.updateItemImages(images);
        }
        db.itemDao().insertItem(itemState);
        return itemState.getItemId();
    }
}
