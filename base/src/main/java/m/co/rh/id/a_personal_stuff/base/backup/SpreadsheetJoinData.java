package m.co.rh.id.a_personal_stuff.base.backup;

import androidx.annotation.Nullable;

import java.util.Map;

import m.co.rh.id.a_personal_stuff.base.entity.Item;

/**
 * Anchor joins shared with module sheet writers: the fully loaded base-DB
 * items (small anchor table) for item-name resolution. Built once per export
 * by :app.
 */
public class SpreadsheetJoinData {

    private final Map<Long, Item> mItemMap;

    public SpreadsheetJoinData(Map<Long, Item> itemMap) {
        mItemMap = itemMap;
    }

    public Map<Long, Item> getItemMap() {
        return mItemMap;
    }

    /**
     * Resolves an item's display name for the Item Name join column; unknown
     * or null ids resolve to the empty string so the cell stays blank.
     */
    public String itemName(@Nullable Long itemId) {
        if (itemId == null) {
            return "";
        }
        Item item = mItemMap.get(itemId);
        if (item == null || item.name == null) {
            return "";
        }
        return item.name;
    }
}
