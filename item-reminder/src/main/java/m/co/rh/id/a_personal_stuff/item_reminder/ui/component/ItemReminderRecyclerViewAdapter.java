package m.co.rh.id.a_personal_stuff.item_reminder.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.provider.command.PagedItemReminderCmd;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class ItemReminderRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedItemReminderCmd mPagedItemReminderCmd;
    private ItemReminderItemSV.OnItemReminderDeleteClicked mOnItemReminderDeleteClicked;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public ItemReminderRecyclerViewAdapter(PagedItemReminderCmd pagedItemReminderCmd,
                                           ItemReminderItemSV.OnItemReminderDeleteClicked onItemReminderDeleteClicked,
                                           INavigator navigator, StatefulView parentStatefulView
    ) {
        mPagedItemReminderCmd = pagedItemReminderCmd;
        mOnItemReminderDeleteClicked = onItemReminderDeleteClicked;
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mCreatedSvList = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Activity activity = mNavigator.getActivity();
        if (VIEW_TYPE_EMPTY_TEXT == viewType) {
            View view = activity.getLayoutInflater().inflate(m.co.rh.id.a_personal_stuff.base.
                    R.layout.no_record, parent, false);
            return new EmptyViewHolder(view);
        } else {
            ItemReminderItemSV itemSV = new ItemReminderItemSV();
            itemSV.setOnItemReminderDeleteClicked(mOnItemReminderDeleteClicked);
            mNavigator.injectRequired(mParentStatefulView, itemSV);
            View view = itemSV.buildView(activity, parent);
            mCreatedSvList.add(itemSV);
            return new ItemViewHolder(view, itemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<ItemReminder> itemArrayList = mPagedItemReminderCmd.getAllItems();
            ItemReminder item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mPagedItemReminderCmd.getAllItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mPagedItemReminderCmd == null) {
            return true;
        }
        return mPagedItemReminderCmd.getAllItems().size() == 0;
    }

    public void notifyItemAdded(ItemReminder item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            ArrayList<ItemReminder> itemStates = mPagedItemReminderCmd.getAllItems();
            itemStates.add(0, item);
            if (itemStates.size() == 1) {
                notifyItemChanged(0);
            } else {
                notifyItemInserted(0);
            }
        }
    }

    public void notifyItemUpdated(ItemReminder item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<ItemReminder> itemStates = mPagedItemReminderCmd.getAllItems();
            itemStates.remove(existingIdx);
            itemStates.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(ItemReminder item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            ArrayList<ItemReminder> itemStates = mPagedItemReminderCmd.getAllItems();
            itemStates.remove(removedIdx);
            notifyItemRemoved(removedIdx);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void dispose(Activity activity) {
        if (!mCreatedSvList.isEmpty()) {
            for (StatefulView sv : mCreatedSvList) {
                sv.dispose(activity);
            }
            mCreatedSvList.clear();
        }
    }

    private int findItem(ItemReminder item) {
        ArrayList<ItemReminder> items =
                mPagedItemReminderCmd.getAllItems();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.createdDateTime.equals(
                    items.get(i).createdDateTime)) {
                removedIdx = i;
                break;
            }
        }
        return removedIdx;
    }

    public void notifyItemRefreshed() {
        notifyItemRangeChanged(0, getItemCount());
    }

    protected static class ItemViewHolder extends RecyclerView.ViewHolder {
        private ItemReminderItemSV mItemReminderItemSV;

        public ItemViewHolder(@NonNull View itemView, ItemReminderItemSV itemReminderItemSV) {
            super(itemView);
            mItemReminderItemSV = itemReminderItemSV;
        }

        public void setItem(ItemReminder itemReminder) {
            mItemReminderItemSV.setItemReminder(itemReminder);
        }

        public ItemReminder getItem() {
            return mItemReminderItemSV.getItemReminder();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
