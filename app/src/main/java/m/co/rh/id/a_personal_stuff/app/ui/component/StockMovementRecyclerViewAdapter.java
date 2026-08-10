package m.co.rh.id.a_personal_stuff.app.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.ui.model.StockMovement;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.a_personal_stuff.item_usage.ui.component.ItemUsageItemSV;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.a_personal_stuff.item_purchase.ui.component.ItemPurchaseItemSV;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings("rawtypes")
public class StockMovementRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_USAGE = 1;
    public static final int VIEW_TYPE_PURCHASE = 2;
    public static final int VIEW_TYPE_EMPTY = 3;

    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final ItemUsageItemSV.OnItemUsageEditClicked mOnItemUsageEditClicked;
    private final ItemUsageItemSV.OnItemUsageDeleteClicked mOnItemUsageDeleteClicked;
    private final ItemPurchaseItemSV.OnItemPurchaseEditClicked mOnItemPurchaseEditClicked;
    private final ItemPurchaseItemSV.OnItemPurchaseDeleteClicked mOnItemPurchaseDeleteClicked;
    private final List<StatefulView> mCreatedSvList;
    // Whether newly created rows should start in compact mode.
    private boolean mCompact;

    /** Full model: every HeaderEntry followed by its rows, in date-desc order. */
    private final List<Object> mAllEntries = new ArrayList<>();
    /** Visible subset: rows of collapsed groups are omitted. */
    private final List<Object> mFlatList = new ArrayList<>();
    /** Expanded state keyed by group (type + firstDate), survives reloads. */
    private final Map<String, Boolean> mExpandedKeys = new HashMap<>();

    private final SimpleDateFormat mDateFormat =
            new SimpleDateFormat("dd MMM", Locale.getDefault());

    public StockMovementRecyclerViewAdapter(
            INavigator navigator,
            StatefulView parentStatefulView,
            ItemUsageItemSV.OnItemUsageEditClicked onItemUsageEditClicked,
            ItemUsageItemSV.OnItemUsageDeleteClicked onItemUsageDeleteClicked,
            ItemPurchaseItemSV.OnItemPurchaseEditClicked onItemPurchaseEditClicked,
            ItemPurchaseItemSV.OnItemPurchaseDeleteClicked onItemPurchaseDeleteClicked) {
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mOnItemUsageEditClicked = onItemUsageEditClicked;
        mOnItemUsageDeleteClicked = onItemUsageDeleteClicked;
        mOnItemPurchaseEditClicked = onItemPurchaseEditClicked;
        mOnItemPurchaseDeleteClicked = onItemPurchaseDeleteClicked;
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
            if (sv instanceof ItemUsageItemSV) {
                ((ItemUsageItemSV) sv).setCompact(compact);
            }
            if (sv instanceof ItemPurchaseItemSV) {
                ((ItemPurchaseItemSV) sv).setCompact(compact);
            }
        }
    }

    public boolean isCompact() {
        return mCompact;
    }

    public void setMovements(List<StockMovement> movements) {
        mAllEntries.clear();
        if (movements == null || movements.isEmpty()) {
            rebuildVisible();
            return;
        }

        StockMovement.Type previousType = null;
        HeaderEntry currentHeader = null;
        for (StockMovement movement : movements) {
            if (previousType == null || movement.type != previousType) {
                int labelResId = movement.type == StockMovement.Type.USAGE
                        ? m.co.rh.id.a_personal_stuff.item_usage.R.string.title_usages
                        : m.co.rh.id.a_personal_stuff.item_purchase.R.string.title_purchases;
                currentHeader = new HeaderEntry(labelResId, movement.type);
                // Carry over expanded state across reloads by stable key.
                currentHeader.expanded = Boolean.TRUE.equals(mExpandedKeys.get(currentHeader.key()));
                mAllEntries.add(currentHeader);
                previousType = movement.type;
            }
            currentHeader.total += movement.signedAmount;
            // List is date-desc: first row is newest, last is oldest.
            if (currentHeader.firstDate == null) {
                currentHeader.firstDate = movement.date;
            }
            currentHeader.lastDate = movement.date;
            mAllEntries.add(movement);
        }
        rebuildVisible();
    }

    /** Rebuild mFlatList from mAllEntries, hiding rows of collapsed groups. */
    private void rebuildVisible() {
        mFlatList.clear();
        HeaderEntry currentGroup = null;
        for (Object entry : mAllEntries) {
            if (entry instanceof HeaderEntry) {
                currentGroup = (HeaderEntry) entry;
                mFlatList.add(currentGroup);
            } else if (currentGroup != null && currentGroup.expanded) {
                mFlatList.add(entry);
            }
        }
        notifyDataSetChanged();
    }

    private void toggleGroup(HeaderEntry header) {
        header.expanded = !header.expanded;
        mExpandedKeys.put(header.key(), header.expanded);
        rebuildVisible();
    }

    @Override
    public int getItemCount() {
        if (mFlatList.isEmpty()) {
            return 1;
        }
        return mFlatList.size();
    }

    private boolean isEmpty() {
        return mFlatList.isEmpty();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY;
        }
        Object item = mFlatList.get(position);
        if (item instanceof HeaderEntry) {
            return VIEW_TYPE_HEADER;
        } else if (item instanceof StockMovement) {
            StockMovement movement = (StockMovement) item;
            return movement.type == StockMovement.Type.USAGE ? VIEW_TYPE_USAGE : VIEW_TYPE_PURCHASE;
        }
        return VIEW_TYPE_EMPTY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Activity activity = mNavigator.getActivity();
        if (VIEW_TYPE_EMPTY == viewType) {
            View view = activity.getLayoutInflater().inflate(m.co.rh.id.a_personal_stuff.base.R.layout.no_record, parent, false);
            return new PlainViewHolder(view);
        } else if (VIEW_TYPE_HEADER == viewType) {
            View view = activity.getLayoutInflater().inflate(R.layout.list_item_stock_movement_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (VIEW_TYPE_USAGE == viewType) {
            ItemUsageItemSV sv = new ItemUsageItemSV(mCompact);
            sv.setOnItemUsageEditClicked(mOnItemUsageEditClicked);
            sv.setOnItemUsageDeleteClicked(mOnItemUsageDeleteClicked);
            mNavigator.injectRequired(mParentStatefulView, sv);
            View view = sv.buildView(activity, parent);
            mCreatedSvList.add(sv);
            return new SvViewHolder(view, sv);
        } else { // VIEW_TYPE_PURCHASE
            ItemPurchaseItemSV sv = new ItemPurchaseItemSV(mCompact);
            sv.setOnItemPurchaseEditClicked(mOnItemPurchaseEditClicked);
            sv.setOnItemPurchaseDeleteClicked(mOnItemPurchaseDeleteClicked);
            mNavigator.injectRequired(mParentStatefulView, sv);
            View view = sv.buildView(activity, parent);
            mCreatedSvList.add(sv);
            return new SvViewHolder(view, sv);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (isEmpty()) {
            return;
        }
        Object item = mFlatList.get(position);
        if (item instanceof HeaderEntry) {
            HeaderEntry header = (HeaderEntry) item;
            bindHeader((HeaderViewHolder) holder, header, position);
        } else if (item instanceof StockMovement) {
            StockMovement movement = (StockMovement) item;
            SvViewHolder svHolder = (SvViewHolder) holder;
            if (movement.type == StockMovement.Type.USAGE) {
                if (svHolder.usageSv != null) {
                    svHolder.usageSv.setItemUsageState((ItemUsageState) movement.sourceState);
                }
            } else {
                if (svHolder.purchaseSv != null) {
                    svHolder.purchaseSv.setItemPurchaseState((ItemPurchaseState) movement.sourceState);
                }
            }
        }
    }

    private void bindHeader(HeaderViewHolder holder, HeaderEntry header, int position) {
        TextView textView = holder.text;
        String label = textView.getResources().getString(header.labelResId);
        // signedAmount is negative for usages and positive for purchases, so the
        // group total is naturally signed. Show the sign explicitly (+/−).
        String sign = header.total > 0 ? "+" : ""; // negatives already carry '−'
        StringBuilder sb = new StringBuilder(label)
                .append(": ").append(sign).append(header.total);
        // Date range: first row is newest, last is oldest (date-desc sort).
        if (header.firstDate != null && header.lastDate != null) {
            sb.append("  (");
            if (header.firstDate.equals(header.lastDate)) {
                sb.append(mDateFormat.format(header.firstDate));
            } else {
                sb.append(mDateFormat.format(header.firstDate))
                        .append(" → ")
                        .append(mDateFormat.format(header.lastDate));
            }
            sb.append(")");
        }
        // Expand/collapse indicator — ▾ expanded, ▸ collapsed.
        sb.append("  ").append(header.expanded ? "▾" : "▸");
        textView.setText(sb.toString());
        int colorRes = header.type == StockMovement.Type.USAGE
                ? android.R.color.holo_red_dark
                : android.R.color.holo_green_dark;
        textView.setTextColor(textView.getResources().getColor(colorRes));
        // Group spacing: extra top gap before a header that follows another group.
        float density = textView.getResources().getDisplayMetrics().density;
        int topPad = position == 0 ? (int) (8 * density) : (int) (20 * density);
        int sidePad = textView.getResources()
                .getDimensionPixelSize(m.co.rh.id.a_personal_stuff.base.R.dimen.text_margin);
        textView.setPadding(sidePad, topPad, sidePad, (int) (8 * density));
        holder.currentHeader = header;
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

    protected static class SvViewHolder extends RecyclerView.ViewHolder {
        ItemUsageItemSV usageSv;
        ItemPurchaseItemSV purchaseSv;

        public SvViewHolder(@NonNull View itemView, ItemUsageItemSV usageSv) {
            super(itemView);
            this.usageSv = usageSv;
        }

        public SvViewHolder(@NonNull View itemView, ItemPurchaseItemSV purchaseSv) {
            super(itemView);
            this.purchaseSv = purchaseSv;
        }
    }

    protected static class PlainViewHolder extends RecyclerView.ViewHolder {
        public PlainViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    /** Holder for section-header rows. Tapping the header toggles its group. */
    protected class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView text;
        HeaderEntry currentHeader;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            text = (TextView) itemView.findViewById(R.id.text_header);
            itemView.setOnClickListener(v -> {
                if (currentHeader != null) {
                    toggleGroup(currentHeader);
                }
            });
        }
    }

    /** A section-header row: label, group type, signed running total, the
     *  group's date range, and whether the group is expanded. */
    private static class HeaderEntry {
        final int labelResId;
        final StockMovement.Type type;
        int total;
        Date firstDate;
        Date lastDate;
        boolean expanded; // default false → collapsed

        HeaderEntry(int labelResId, StockMovement.Type type) {
            this.labelResId = labelResId;
            this.type = type;
        }

        /** Stable identity for this group across reloads (type + first row date). */
        String key() {
            return type.name() + "|" + (firstDate == null ? "" : firstDate.getTime());
        }
    }
}
