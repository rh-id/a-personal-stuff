package m.co.rh.id.a_personal_stuff.item_checklist.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class ItemChecklistEntryRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_ITEM = 0;
    public static final int VIEW_TYPE_EMPTY_TEXT = 1;

    private ArrayList<ItemChecklistEntrySV.ItemChecklistEntry> mEntries;
    private ItemChecklistEntrySV.OnItemChecklistEntryDeleteClicked mOnItemChecklistEntryDeleteClicked;
    private ItemChecklistEntrySV.OnItemChecklistEntryCheckClicked mOnItemChecklistEntryCheckClicked;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvList;

    public ItemChecklistEntryRecyclerViewAdapter(ItemChecklistEntrySV.OnItemChecklistEntryDeleteClicked onItemChecklistEntryDeleteClicked,
                                                 ItemChecklistEntrySV.OnItemChecklistEntryCheckClicked onItemChecklistEntryCheckClicked,
                                                 INavigator navigator, StatefulView parentStatefulView
    ) {
        mEntries = new ArrayList<>();
        mOnItemChecklistEntryDeleteClicked = onItemChecklistEntryDeleteClicked;
        mOnItemChecklistEntryCheckClicked = onItemChecklistEntryCheckClicked;
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mCreatedSvList = new ArrayList<>();
    }

    public void setEntries(ArrayList<ItemChecklistEntrySV.ItemChecklistEntry> entries) {
        mEntries = entries;
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
            ItemChecklistEntrySV itemChecklistEntrySV = new ItemChecklistEntrySV();
            itemChecklistEntrySV.setOnItemChecklistEntryDeleteClicked(mOnItemChecklistEntryDeleteClicked);
            itemChecklistEntrySV.setOnItemChecklistEntryCheckClicked(mOnItemChecklistEntryCheckClicked);
            mNavigator.injectRequired(mParentStatefulView, itemChecklistEntrySV);
            View view = itemChecklistEntrySV.buildView(activity, parent);
            mCreatedSvList.add(itemChecklistEntrySV);
            return new EntryViewHolder(view, itemChecklistEntrySV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof EntryViewHolder) {
            ItemChecklistEntrySV.ItemChecklistEntry entry = mEntries.get(position);
            EntryViewHolder entryViewHolder = (EntryViewHolder) holder;
            entryViewHolder.setEntry(entry);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mEntries.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_ITEM;
    }

    private boolean isEmpty() {
        return mEntries.size() == 0;
    }

    public void notifyEntryAdded(ItemChecklistEntrySV.ItemChecklistEntry entry) {
        int existingIdx = findEntry(entry);
        if (existingIdx == -1) {
            mEntries.add(0, entry);
            if (mEntries.size() == 1) {
                notifyItemChanged(0);
            } else {
                notifyItemInserted(0);
            }
        }
    }

    public void notifyEntryUpdated(ItemChecklistEntrySV.ItemChecklistEntry entry) {
        int existingIdx = findEntry(entry);
        if (existingIdx != -1) {
            mEntries.remove(existingIdx);
            mEntries.add(existingIdx, entry);
            notifyItemChanged(existingIdx);
        }
    }

    public void notifyEntryDeleted(ItemChecklistEntrySV.ItemChecklistEntry entry) {
        int removedIdx = findEntry(entry);
        if (removedIdx != -1) {
            mEntries.remove(removedIdx);
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

    private int findEntry(ItemChecklistEntrySV.ItemChecklistEntry entry) {
        int size = mEntries.size();
        int foundIdx = -1;
        for (int i = 0; i < size; i++) {
            ItemChecklistEntrySV.ItemChecklistEntry current = mEntries.get(i);
            if (entry.itemChecklistItem != null && current.itemChecklistItem != null &&
                    entry.itemChecklistItem.id.equals(current.itemChecklistItem.id)) {
                foundIdx = i;
                break;
            }
        }
        return foundIdx;
    }

    protected static class EntryViewHolder extends RecyclerView.ViewHolder {
        private ItemChecklistEntrySV mItemChecklistEntrySV;

        public EntryViewHolder(@NonNull View itemView, ItemChecklistEntrySV itemChecklistEntrySV) {
            super(itemView);
            mItemChecklistEntrySV = itemChecklistEntrySV;
        }

        public void setEntry(ItemChecklistEntrySV.ItemChecklistEntry itemChecklistEntry) {
            mItemChecklistEntrySV.setItemChecklistEntry(itemChecklistEntry);
        }

        public ItemChecklistEntrySV.ItemChecklistEntry getEntry() {
            return mItemChecklistEntrySV.getItemChecklistEntry();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
