package m.co.rh.id.a_personal_stuff.item_purchase.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.PagedItemPurchaseCmd;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class ItemPurchaseRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private PagedItemPurchaseCmd mPagedItemPurchaseCmd;
    private ItemPurchaseItemSV.OnItemPurchaseEditClicked mOnItemPurchaseEditClicked;
    private ItemPurchaseItemSV.OnItemPurchaseDeleteClicked mOnItemPurchaseDeleteClicked;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;
    // Whether newly created rows should start in compact mode.
    private boolean mCompact;

    public ItemPurchaseRecyclerViewAdapter(PagedItemPurchaseCmd pagedItemPurchaseCmd,
                                          ItemPurchaseItemSV.OnItemPurchaseEditClicked onItemPurchaseEditClicked,
                                          ItemPurchaseItemSV.OnItemPurchaseDeleteClicked onItemPurchaseDeleteClicked,
                                          INavigator navigator, StatefulView parentStatefulView
    ) {
        mPagedItemPurchaseCmd = pagedItemPurchaseCmd;
        mOnItemPurchaseEditClicked = onItemPurchaseEditClicked;
        mOnItemPurchaseDeleteClicked = onItemPurchaseDeleteClicked;
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
            if (sv instanceof ItemPurchaseItemSV) {
                ((ItemPurchaseItemSV) sv).setCompact(compact);
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
            ItemPurchaseItemSV itemPurchaseItemSV = new ItemPurchaseItemSV(mCompact);
            itemPurchaseItemSV.setOnItemPurchaseEditClicked(mOnItemPurchaseEditClicked);
            itemPurchaseItemSV.setOnItemPurchaseDeleteClicked(mOnItemPurchaseDeleteClicked);
            mNavigator.injectRequired(mParentStatefulView, itemPurchaseItemSV);
            View view = itemPurchaseItemSV.buildView(activity, parent);
            mCreatedSvList.add(itemPurchaseItemSV);
            return new ItemViewHolder(view, itemPurchaseItemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ArrayList<ItemPurchaseState> itemArrayList = mPagedItemPurchaseCmd.getAllItems();
            ItemPurchaseState item = itemArrayList.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mPagedItemPurchaseCmd.getAllItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        if (mPagedItemPurchaseCmd == null) {
            return true;
        }
        return mPagedItemPurchaseCmd.getAllItems().size() == 0;
    }

    public void notifyItemAdded(ItemPurchaseState item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            ArrayList<ItemPurchaseState> itemStates = mPagedItemPurchaseCmd.getAllItems();
            itemStates.add(0, item);
            if (itemStates.size() == 1) {
                notifyItemChanged(0);
            } else {
                notifyItemInserted(0);
            }
        }
    }

    public void notifyItemUpdated(ItemPurchaseState item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            ArrayList<ItemPurchaseState> itemStates = mPagedItemPurchaseCmd.getAllItems();
            itemStates.remove(existingIdx);
            itemStates.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(ItemPurchaseState item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            ArrayList<ItemPurchaseState> itemStates = mPagedItemPurchaseCmd.getAllItems();
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

    private int findItem(ItemPurchaseState item) {
        ArrayList<ItemPurchaseState> items =
                mPagedItemPurchaseCmd.getAllItems();
        int size = items.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.getItemPurchaseCreatedDateTime().equals(
                    items.get(i).getItemPurchaseCreatedDateTime())) {
                removedIdx = i;
                break;
            }
        }
        return removedIdx;
    }

    public void notifyItemRefreshed() {
        notifyDataSetChanged();
    }

    protected static class ItemViewHolder extends RecyclerView.ViewHolder {
        private ItemPurchaseItemSV mItemPurchaseItemSV;

        public ItemViewHolder(@NonNull View itemView, ItemPurchaseItemSV itemPurchaseItemSV) {
            super(itemView);
            mItemPurchaseItemSV = itemPurchaseItemSV;
        }

        public void setItem(ItemPurchaseState itemPurchaseState) {
            mItemPurchaseItemSV.setItemPurchaseState(itemPurchaseState);
        }

        public ItemPurchaseState getItem() {
            return mItemPurchaseItemSV.getItemPurchaseState();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
