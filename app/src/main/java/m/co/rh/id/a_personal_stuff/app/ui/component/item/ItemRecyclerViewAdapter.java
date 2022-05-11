package m.co.rh.id.a_personal_stuff.app.ui.component.item;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_personal_stuff.app.provider.command.PagedItemCmd;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class ItemRecyclerViewAdapter extends ItemAdapter {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedItemCmd mPagedItemCmd;
    private ItemItemSV.OnItemEditClicked mOnItemEditClicked;
    private ItemItemSV.OnItemDeleteClicked mOnItemDeleteClicked;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public ItemRecyclerViewAdapter(PagedItemCmd pagedItemCmd,
                                   ItemItemSV.OnItemEditClicked onItemEditClicked,
                                   ItemItemSV.OnItemDeleteClicked onItemDeleteClicked,
                                   INavigator navigator, StatefulView parentStatefulView
    ) {
        mPagedItemCmd = pagedItemCmd;
        mOnItemEditClicked = onItemEditClicked;
        mOnItemDeleteClicked = onItemDeleteClicked;
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
            ItemItemSV itemSV = new ItemItemSV();
            itemSV.setOnItemEditClicked(mOnItemEditClicked);
            itemSV.setOnItemDeleteClicked(mOnItemDeleteClicked);
            mNavigator.injectRequired(mParentStatefulView, itemSV);
            View view = itemSV.buildView(activity, parent);
            mCreatedSvList.add(itemSV);
            return new ItemViewHolder(view, itemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<ItemState> itemArrayList = mPagedItemCmd.getAllItems();
            ItemState item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mPagedItemCmd.getAllItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mPagedItemCmd == null) {
            return true;
        }
        return mPagedItemCmd.getAllItems().size() == 0;
    }

    @Override
    public void notifyItemAdded(ItemState item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            ArrayList<ItemState> itemStates = mPagedItemCmd.getAllItems();
            itemStates.add(0, item);
            if (itemStates.size() == 1) {
                notifyItemChanged(0);
            } else {
                notifyItemInserted(0);
            }
        }
    }

    @Override
    public void notifyItemUpdated(ItemState item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<ItemState> itemStates = mPagedItemCmd.getAllItems();
            itemStates.remove(existingIdx);
            itemStates.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    @Override
    public void notifyItemDeleted(ItemState item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            ArrayList<ItemState> itemStates = mPagedItemCmd.getAllItems();
            itemStates.remove(removedIdx);
            notifyItemRemoved(removedIdx);
        }
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void dispose(Activity activity) {
        if (!mCreatedSvList.isEmpty()) {
            for (StatefulView sv : mCreatedSvList) {
                sv.dispose(activity);
            }
            mCreatedSvList.clear();
        }
    }

    private int findItem(ItemState item) {
        ArrayList<ItemState> items =
                mPagedItemCmd.getAllItems();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.getItemCreatedDateTime().equals(
                    items.get(i).getItemCreatedDateTime())) {
                removedIdx = i;
                break;
            }
        }
        return removedIdx;
    }

    @Override
    public void notifyItemRefreshed() {
        notifyItemRangeChanged(0, getItemCount());
    }

    protected static class ItemViewHolder extends RecyclerView.ViewHolder {
        private ItemItemSV mItemSV;

        public ItemViewHolder(@NonNull View itemView, ItemItemSV itemSV) {
            super(itemView);
            mItemSV = itemSV;
        }

        public void setItem(ItemState itemState) {
            mItemSV.setItemState(itemState);
        }

        public ItemState getItem() {
            return mItemSV.getItemState();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
