package m.co.rh.id.a_personal_stuff.item_usage.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.PagedItemUsageCmd;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class ItemUsageRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedItemUsageCmd mPagedItemUsageCmd;
    private ItemUsageItemSV.OnItemUsageEditClicked mOnItemUsageEditClicked;
    private ItemUsageItemSV.OnItemUsageDeleteClicked mOnItemUsageDeleteClicked;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public ItemUsageRecyclerViewAdapter(PagedItemUsageCmd pagedItemUsageCmd,
                                        ItemUsageItemSV.OnItemUsageEditClicked onItemUsageEditClicked,
                                        ItemUsageItemSV.OnItemUsageDeleteClicked onItemUsageDeleteClicked,
                                        INavigator navigator, StatefulView parentStatefulView
    ) {
        mPagedItemUsageCmd = pagedItemUsageCmd;
        mOnItemUsageEditClicked = onItemUsageEditClicked;
        mOnItemUsageDeleteClicked = onItemUsageDeleteClicked;
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
            ItemUsageItemSV itemUsageItemSV = new ItemUsageItemSV();
            itemUsageItemSV.setOnItemUsageEditClicked(mOnItemUsageEditClicked);
            itemUsageItemSV.setOnItemUsageDeleteClicked(mOnItemUsageDeleteClicked);
            mNavigator.injectRequired(mParentStatefulView, itemUsageItemSV);
            View view = itemUsageItemSV.buildView(activity, parent);
            mCreatedSvList.add(itemUsageItemSV);
            return new ItemViewHolder(view, itemUsageItemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<ItemUsageState> itemArrayList = mPagedItemUsageCmd.getAllItems();
            ItemUsageState item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mPagedItemUsageCmd.getAllItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mPagedItemUsageCmd == null) {
            return true;
        }
        return mPagedItemUsageCmd.getAllItems().size() == 0;
    }

    public void notifyItemAdded(ItemUsageState item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            ArrayList<ItemUsageState> itemStates = mPagedItemUsageCmd.getAllItems();
            itemStates.add(0, item);
            if (itemStates.size() == 1) {
                notifyItemChanged(0);
            } else {
                notifyItemInserted(0);
            }
        }
    }

    public void notifyItemUpdated(ItemUsageState item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<ItemUsageState> itemStates = mPagedItemUsageCmd.getAllItems();
            itemStates.remove(existingIdx);
            itemStates.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(ItemUsageState item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            ArrayList<ItemUsageState> itemStates = mPagedItemUsageCmd.getAllItems();
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

    private int findItem(ItemUsageState item) {
        ArrayList<ItemUsageState> items =
                mPagedItemUsageCmd.getAllItems();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.getItemUsageCreatedDateTime().equals(
                    items.get(i).getItemUsageCreatedDateTime())) {
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
        private ItemUsageItemSV mItemUsageItemSV;

        public ItemViewHolder(@NonNull View itemView, ItemUsageItemSV itemUsageItemSV) {
            super(itemView);
            mItemUsageItemSV = itemUsageItemSV;
        }

        public void setItem(ItemUsageState itemUsageState) {
            mItemUsageItemSV.setItemUsageState(itemUsageState);
        }

        public ItemUsageState getItem() {
            return mItemUsageItemSV.getItemUsageState();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
