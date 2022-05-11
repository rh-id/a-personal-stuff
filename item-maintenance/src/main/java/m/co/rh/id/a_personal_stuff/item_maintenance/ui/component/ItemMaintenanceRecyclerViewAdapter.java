package m.co.rh.id.a_personal_stuff.item_maintenance.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.PagedItemMaintenanceCmd;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class ItemMaintenanceRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedItemMaintenanceCmd mPagedItemMaintenanceCmd;
    private ItemMaintenanceItemSV.OnItemMaintenanceEditClicked mOnItemMaintenanceEditClicked;
    private ItemMaintenanceItemSV.OnItemMaintenanceDeleteClicked mOnItemMaintenanceDeleteClicked;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public ItemMaintenanceRecyclerViewAdapter(PagedItemMaintenanceCmd pagedItemMaintenanceCmd,
                                              ItemMaintenanceItemSV.OnItemMaintenanceEditClicked onItemMaintenanceEditClicked,
                                              ItemMaintenanceItemSV.OnItemMaintenanceDeleteClicked onItemMaintenanceDeleteClicked,
                                              INavigator navigator, StatefulView parentStatefulView
    ) {
        mPagedItemMaintenanceCmd = pagedItemMaintenanceCmd;
        mOnItemMaintenanceEditClicked = onItemMaintenanceEditClicked;
        mOnItemMaintenanceDeleteClicked = onItemMaintenanceDeleteClicked;
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
            ItemMaintenanceItemSV itemMaintenanceItemSV = new ItemMaintenanceItemSV();
            itemMaintenanceItemSV.setOnItemMaintenanceEditClicked(mOnItemMaintenanceEditClicked);
            itemMaintenanceItemSV.setOnItemMaintenanceDeleteClicked(mOnItemMaintenanceDeleteClicked);
            mNavigator.injectRequired(mParentStatefulView, itemMaintenanceItemSV);
            View view = itemMaintenanceItemSV.buildView(activity, parent);
            mCreatedSvList.add(itemMaintenanceItemSV);
            return new ItemViewHolder(view, itemMaintenanceItemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<ItemMaintenanceState> itemArrayList = mPagedItemMaintenanceCmd.getAllItems();
            ItemMaintenanceState item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mPagedItemMaintenanceCmd.getAllItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mPagedItemMaintenanceCmd == null) {
            return true;
        }
        return mPagedItemMaintenanceCmd.getAllItems().size() == 0;
    }

    public void notifyItemAdded(ItemMaintenanceState item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            ArrayList<ItemMaintenanceState> itemStates = mPagedItemMaintenanceCmd.getAllItems();
            itemStates.add(0, item);
            if (itemStates.size() == 1) {
                notifyItemChanged(0);
            } else {
                notifyItemInserted(0);
            }
        }
    }

    public void notifyItemUpdated(ItemMaintenanceState item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<ItemMaintenanceState> itemStates = mPagedItemMaintenanceCmd.getAllItems();
            itemStates.remove(existingIdx);
            itemStates.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(ItemMaintenanceState item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            ArrayList<ItemMaintenanceState> itemStates = mPagedItemMaintenanceCmd.getAllItems();
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

    private int findItem(ItemMaintenanceState item) {
        ArrayList<ItemMaintenanceState> items =
                mPagedItemMaintenanceCmd.getAllItems();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.getItemMaintenanceCreatedDateTime().equals(
                    items.get(i).getItemMaintenanceCreatedDateTime())) {
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
        private ItemMaintenanceItemSV mItemMaintenanceItemSV;

        public ItemViewHolder(@NonNull View itemView, ItemMaintenanceItemSV itemMaintenanceItemSV) {
            super(itemView);
            mItemMaintenanceItemSV = itemMaintenanceItemSV;
        }

        public void setItem(ItemMaintenanceState itemMaintenanceState) {
            mItemMaintenanceItemSV.setItemMaintenanceState(itemMaintenanceState);
        }

        public ItemMaintenanceState getItem() {
            return mItemMaintenanceItemSV.getItemMaintenanceState();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
