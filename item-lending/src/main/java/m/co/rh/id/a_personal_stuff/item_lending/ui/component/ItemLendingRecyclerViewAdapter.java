package m.co.rh.id.a_personal_stuff.item_lending.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.PagedItemLendingCmd;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class ItemLendingRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedItemLendingCmd mPagedItemLendingCmd;
    private ItemLendingItemSV.OnItemLendingEditClicked mOnItemLendingEditClicked;
    private ItemLendingItemSV.OnItemLendingDeleteClicked mOnItemLendingDeleteClicked;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;
    // Whether newly created rows should start in compact mode.
    private boolean mCompact;

    public ItemLendingRecyclerViewAdapter(PagedItemLendingCmd pagedItemLendingCmd,
                                          ItemLendingItemSV.OnItemLendingEditClicked onItemLendingEditClicked,
                                          ItemLendingItemSV.OnItemLendingDeleteClicked onItemLendingDeleteClicked,
                                          INavigator navigator, StatefulView parentStatefulView
    ) {
        mPagedItemLendingCmd = pagedItemLendingCmd;
        mOnItemLendingEditClicked = onItemLendingEditClicked;
        mOnItemLendingDeleteClicked = onItemLendingDeleteClicked;
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mCreatedSvList = new ArrayList<>();
    }

    /**
     * Switch every row between detailed and compact. Each row re-applies the
     * matching ConstraintSet to its existing view tree (no re-inflation, no
     * notifyDataSetChanged), so this just delegates to the rows.
     */
    public void setCompact(boolean compact) {
        if (mCompact == compact) {
            return;
        }
        mCompact = compact;
        for (StatefulView sv : mCreatedSvList) {
            if (sv instanceof ItemLendingItemSV) {
                ((ItemLendingItemSV) sv).setCompact(compact);
            }
        }
    }

    public boolean isCompact() {
        return mCompact;
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
            ItemLendingItemSV itemLendingItemSV = new ItemLendingItemSV(mCompact);
            itemLendingItemSV.setOnItemLendingEditClicked(mOnItemLendingEditClicked);
            itemLendingItemSV.setOnItemLendingDeleteClicked(mOnItemLendingDeleteClicked);
            mNavigator.injectRequired(mParentStatefulView, itemLendingItemSV);
            View view = itemLendingItemSV.buildView(activity, parent);
            mCreatedSvList.add(itemLendingItemSV);
            return new ItemViewHolder(view, itemLendingItemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<ItemLendingState> itemArrayList = mPagedItemLendingCmd.getAllItems();
            ItemLendingState item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mPagedItemLendingCmd.getAllItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mPagedItemLendingCmd == null) {
            return true;
        }
        return mPagedItemLendingCmd.getAllItems().size() == 0;
    }

    public void notifyItemAdded(ItemLendingState item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            ArrayList<ItemLendingState> itemStates = mPagedItemLendingCmd.getAllItems();
            itemStates.add(0, item);
            if (itemStates.size() == 1) {
                notifyItemChanged(0);
            } else {
                notifyItemInserted(0);
            }
        }
    }

    public void notifyItemUpdated(ItemLendingState item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<ItemLendingState> itemStates = mPagedItemLendingCmd.getAllItems();
            itemStates.remove(existingIdx);
            itemStates.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(ItemLendingState item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            ArrayList<ItemLendingState> itemStates = mPagedItemLendingCmd.getAllItems();
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

    private int findItem(ItemLendingState item) {
        // Identity is the persisted id, not lend_date_time: the lend date is a
        // user-picked value two lendings can share, which would make
        // notifyItemAdded skip the second row and updated/deleted hit the
        // wrong one. The id is assigned by the insert and carried through
        // clones/notifier events, so every row here has one.
        ArrayList<ItemLendingState> items =
                mPagedItemLendingCmd.getAllItems();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.getItemLendingId().equals(
                    items.get(i).getItemLendingId())) {
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
        private ItemLendingItemSV mItemLendingItemSV;

        public ItemViewHolder(@NonNull View itemView, ItemLendingItemSV itemLendingItemSV) {
            super(itemView);
            mItemLendingItemSV = itemLendingItemSV;
        }

        public void setItem(ItemLendingState itemLendingState) {
            mItemLendingItemSV.setItemLendingState(itemLendingState);
        }

        public ItemLendingState getItem() {
            return mItemLendingItemSV.getItemLendingState();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
