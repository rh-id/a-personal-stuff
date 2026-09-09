package m.co.rh.id.a_personal_stuff.app.ui.model;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;

public class DashboardData {
    public int totalItems;
    /** Nullable — null when the inventory has no valued items. */
    public Double inventoryValue;
    public int expiredCount;
    public int expiringCount;
    public List<Item> expiredSamples = new ArrayList<>();
    public List<Item> expiringSamples = new ArrayList<>();
    public int lowStockCount;
    public List<Item> lowStockSamples = new ArrayList<>();
    public List<ItemReminder> upcomingReminders = new ArrayList<>();
    public int upcomingReminderCount;
}
