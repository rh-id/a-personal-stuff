package m.co.rh.id.a_personal_stuff.item_checklist.model;

import androidx.room.Ignore;

import java.io.Serializable;

public class ItemChecklistProgress implements Serializable, Cloneable {
    public Long itemChecklistId;
    public int total;
    public int checked;

    public ItemChecklistProgress() {
    }

    @Ignore
    public ItemChecklistProgress(Long itemChecklistId, int total, int checked) {
        this.itemChecklistId = itemChecklistId;
        this.total = total;
        this.checked = checked;
    }
}
