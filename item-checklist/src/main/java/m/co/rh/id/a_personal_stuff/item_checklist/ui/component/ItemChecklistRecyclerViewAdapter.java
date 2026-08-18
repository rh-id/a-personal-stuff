package m.co.rh.id.a_personal_stuff.item_checklist.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistProgress;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class ItemChecklistRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private final ArrayList<ItemChecklistState> mItems;
    private ItemChecklistItemSV.OnItemChecklistEditClicked mOnItemChecklistEditClicked;
    private ItemChecklistItemSV.OnItemChecklistDeleteClicked mOnItemChecklistDeleteClicked;
    private ItemChecklistItemSV.OnItemChecklistSelected mOnItemChecklistSelected;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;
    private Map<Long, ItemChecklistProgress> mProgressMap;
    private final boolean mSelectMode;
    // Whether newly created rows should start in compact mode.
    private boolean mCompact;

    public ItemChecklistRecyclerViewAdapter(ItemChecklistItemSV.OnItemChecklistEditClicked onItemChecklistEditClicked,
                                            ItemChecklistItemSV.OnItemChecklistDeleteClicked onItemChecklistDeleteClicked,
                                            INavigator navigator, StatefulView parentStatefulView
    ) {
        mItems = new ArrayList<>();
        mOnItemChecklistEditClicked = onItemChecklistEditClicked;
        mOnItemChecklistDeleteClicked = onItemChecklistDeleteClicked;
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mCreatedSvList = new ArrayList<>();
        mProgressMap = new HashMap<>();
        mSelectMode = false;
        mCompact = false;
    }

    public ItemChecklistRecyclerViewAdapter(ItemChecklistItemSV.OnItemChecklistSelected onItemChecklistSelected,
                                            INavigator navigator, StatefulView parentStatefulView
    ) {
        mItems = new ArrayList<>();
        mOnItemChecklistSelected = onItemChecklistSelected;
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mCreatedSvList = new ArrayList<>();
        mProgressMap = new HashMap<>();
        mSelectMode = true;
        mCompact = false;
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
            if (sv instanceof ItemChecklistItemSV) {
                ((ItemChecklistItemSV) sv).setCompact(compact);
            }
        }
    }

    public boolean isCompact() {
        return mCompact;
    }

    public void setProgressMap(List<ItemChecklistProgress> progressList) {
        mProgressMap.clear();
        if (progressList != null) {
            for (ItemChecklistProgress progress : progressList) {
                mProgressMap.put(progress.itemChecklistId, progress);
            }
        }
    }

    public void setItems(List<ItemChecklistState> items) {
        mItems.clear();
        if (items != null) {
            mItems.addAll(items);
        }
        notifyDataSetChanged();
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
            ItemChecklistItemSV itemChecklistItemSV;
            if (mSelectMode) {
                itemChecklistItemSV = new ItemChecklistItemSV(true, mCompact);
                itemChecklistItemSV.setOnItemChecklistSelected(mOnItemChecklistSelected);
            } else {
                itemChecklistItemSV = new ItemChecklistItemSV(false, mCompact);
                itemChecklistItemSV.setOnItemChecklistEditClicked(mOnItemChecklistEditClicked);
                itemChecklistItemSV.setOnItemChecklistDeleteClicked(mOnItemChecklistDeleteClicked);
            }
            mNavigator.injectRequired(mParentStatefulView, itemChecklistItemSV);
            View view = itemChecklistItemSV.buildView(activity, parent);
            mCreatedSvList.add(itemChecklistItemSV);
            return new ItemViewHolder(view, itemChecklistItemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ItemViewHolder) {
            ItemChecklistState item = mItems.get(position);
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            itemViewHolder.setItem(item);
            ItemChecklistProgress progress = mProgressMap.get(item.getChecklistId());
            if (progress == null) {
                progress = new ItemChecklistProgress(item.getChecklistId(), 0, 0);
            }
            itemViewHolder.setProgress(progress);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        return mItems.size() == 0;
    }

    public void notifyItemAdded(ItemChecklistState item) {
        int existingIdx = findItem(item);
        if (existingIdx == -1) {
            mItems.add(0, item);
            if (mItems.size() == 1) {
                notifyItemChanged(0);
            } else {
                notifyItemInserted(0);
            }
        }
    }

    public void notifyItemUpdated(ItemChecklistState item) {
        int existingIdx = findItem(item);
        if (existingIdx != -1) {
            mItems.remove(existingIdx);
            mItems.add(existingIdx, item);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyItemDeleted(ItemChecklistState item) {
        int removedIdx = findItem(item);
        if (removedIdx != -1) {
            mItems.remove(removedIdx);
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

    private int findItem(ItemChecklistState item) {
        int size = mItems.size();
        int removedIdx = -1;
        for (int i = 0; i < size; i++) {
            if (item.getChecklistId() != null && item.getChecklistId().equals(mItems.get(i).getChecklistId())) {
                removedIdx = i;
                break;
            }
        }
        return removedIdx;
    }

    protected static class ItemViewHolder extends RecyclerView.ViewHolder {
        private ItemChecklistItemSV mItemChecklistItemSV;

        public ItemViewHolder(@NonNull View itemView, ItemChecklistItemSV itemChecklistItemSV) {
            super(itemView);
            mItemChecklistItemSV = itemChecklistItemSV;
        }

        public void setItem(ItemChecklistState itemChecklistState) {
            mItemChecklistItemSV.setItemChecklistState(itemChecklistState);
        }

        public void setProgress(ItemChecklistProgress progress) {
            mItemChecklistItemSV.setItemChecklistProgress(progress);
        }

        public ItemChecklistState getItem() {
            return mItemChecklistItemSV.getItemChecklistState();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
