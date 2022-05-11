package m.co.rh.id.a_personal_stuff.item_maintenance.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenance;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;

@Dao
public abstract class ItemMaintenanceDao {

    @Insert
    public abstract long insert(ItemMaintenance itemMaintenance);

    @Update
    public abstract void update(ItemMaintenance itemMaintenance);

    @Delete
    public abstract void delete(ItemMaintenance itemMaintenance);

    @Insert
    public abstract long insert(ItemMaintenanceImage itemMaintenanceImage);

    @Delete
    public abstract void delete(ItemMaintenanceImage itemMaintenanceImage);

    @Transaction
    public void insertItemMaintenance(ItemMaintenanceState itemMaintenanceState) {
        ItemMaintenance itemMaintenance = itemMaintenanceState.getItemMaintenance();
        if (itemMaintenance != null) {
            Long itemMaintenanceId = insert(itemMaintenance);
            itemMaintenance.id = itemMaintenanceId;
            Collection<ItemMaintenanceImage> itemMaintenanceImages = itemMaintenanceState.getItemMaintenanceImages();
            if (!itemMaintenanceImages.isEmpty()) {
                for (ItemMaintenanceImage itemMaintenanceImage : itemMaintenanceImages) {
                    itemMaintenanceImage.itemMaintenanceId = itemMaintenanceId;
                    itemMaintenanceImage.id = insert(itemMaintenanceImage);
                }
            }
        }
    }

    @Transaction
    public void updateItemMaintenance(ItemMaintenanceState itemMaintenanceState) {
        ItemMaintenance itemMaintenance = itemMaintenanceState.getItemMaintenance();
        if (itemMaintenance != null) {
            update(itemMaintenance);
        }
    }

    @Query("SELECT * FROM item_maintenance_image WHERE file_name = :fileName")
    public abstract ItemMaintenanceImage findItemMaintenanceImageByFileName(String fileName);

    @Transaction
    public void deleteItemMaintenanceStatesByItemId(long itemId) {
        List<ItemMaintenance> itemMaintenances = findItemMaintenanceByItemId(itemId);
        if (!itemMaintenances.isEmpty()) {
            for (ItemMaintenance itemMaintenance : itemMaintenances) {
                deleteItemMaintenance(itemMaintenance);
            }
        }
    }

    @Query("SELECT * FROM item_maintenance WHERE item_id = :itemId")
    protected abstract List<ItemMaintenance> findItemMaintenanceByItemId(long itemId);

    // delete ItemMaintenance and all its dependencies
    private void deleteItemMaintenance(ItemMaintenance itemMaintenance) {
        delete(itemMaintenance);
        deleteItemMaintenanceImagesByItemMaintenanceId(itemMaintenance.id);
    }

    @Query("DELETE FROM item_maintenance_image WHERE item_maintenance_id = :itemMaintenanceId")
    protected abstract void deleteItemMaintenanceImagesByItemMaintenanceId(long itemMaintenanceId);

    @Transaction
    public void deleteItemMaintenanceState(ItemMaintenanceState itemMaintenanceState) {
        ItemMaintenance itemMaintenance = itemMaintenanceState.getItemMaintenance();
        if (itemMaintenance != null) {
            deleteItemMaintenance(itemMaintenance);
        }
    }

    @Transaction
    public void insertItemMaintenanceImage(ItemMaintenanceImage itemMaintenanceImage) {
        itemMaintenanceImage.id = insert(itemMaintenanceImage);
    }

    @Query("SELECT * FROM item_maintenance WHERE id = :id")
    protected abstract ItemMaintenance findItemMaintenanceById(long id);

    @Query("SELECT * FROM item_maintenance_image WHERE item_maintenance_id = :itemMaintenanceId")
    protected abstract List<ItemMaintenanceImage> findItemMaintenanceImageByItemMaintenanceId(long itemMaintenanceId);

    public ItemMaintenanceState findItemMaintenanceStateById(long id) {
        return prepareItemMaintenanceState(findItemMaintenanceById(id));
    }

    @Nullable
    private ItemMaintenanceState prepareItemMaintenanceState(ItemMaintenance itemMaintenance) {
        if (itemMaintenance != null) {
            ItemMaintenanceState itemMaintenanceState = new ItemMaintenanceState();
            itemMaintenanceState.updateItemMaintenance(itemMaintenance);
            List<ItemMaintenanceImage> itemMaintenanceImages = findItemMaintenanceImageByItemMaintenanceId(itemMaintenance.id);
            if (!itemMaintenanceImages.isEmpty()) {
                itemMaintenanceState.updateItemMaintenanceImages(itemMaintenanceImages);
            }
            return itemMaintenanceState;
        }
        return null;
    }

    public List<ItemMaintenanceState> findItemMaintenanceStateByItemIdWithLimit(long itemId, int limit) {
        List<ItemMaintenance> itemMaintenances = findItemMaintenanceByItemIdWithLimit(itemId, limit);
        List<ItemMaintenanceState> itemMaintenanceStates = new ArrayList<>();
        if (!itemMaintenances.isEmpty()) {
            for (ItemMaintenance itemMaintenance : itemMaintenances) {
                itemMaintenanceStates.add(prepareItemMaintenanceState(itemMaintenance));
            }
        }
        return itemMaintenanceStates;
    }

    @Query("SELECT * FROM item_maintenance WHERE item_id = :itemId" +
            " ORDER BY maintenance_date_time DESC" +
            " LIMIT :limit")
    protected abstract List<ItemMaintenance> findItemMaintenanceByItemIdWithLimit(long itemId, int limit);

    public List<ItemMaintenanceState> searchItemMaintenanceStateByItemId(long itemId, String search) {
        List<ItemMaintenance> itemMaintenances = searchItemMaintenanceByItemId(itemId, search);
        List<ItemMaintenanceState> itemMaintenanceStates = new ArrayList<>();
        if (!itemMaintenances.isEmpty()) {
            for (ItemMaintenance itemMaintenance : itemMaintenances) {
                itemMaintenanceStates.add(prepareItemMaintenanceState(itemMaintenance));
            }
        }
        return itemMaintenanceStates;
    }

    @Query("SELECT * FROM item_maintenance WHERE item_id = :itemId AND description LIKE '%'||:search||'%'")
    protected abstract List<ItemMaintenance> searchItemMaintenanceByItemId(long itemId, String search);

    @Query("SELECT * FROM item_maintenance WHERE description LIKE '%'||:search||'%'")
    public abstract List<ItemMaintenance> searchItemMaintenanceDescription(String search);
}
