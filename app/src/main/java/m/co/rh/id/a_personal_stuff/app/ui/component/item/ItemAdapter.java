package m.co.rh.id.a_personal_stuff.app.ui.component.item;

import android.app.Activity;

import androidx.recyclerview.widget.RecyclerView;

import m.co.rh.id.a_personal_stuff.base.model.ItemState;

public abstract class ItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    abstract void notifyItemAdded(ItemState item);

    abstract void notifyItemUpdated(ItemState item);

    abstract void notifyItemDeleted(ItemState item);

    abstract void dispose(Activity activity);

    abstract void notifyItemRefreshed();

}
