package m.co.rh.id.a_personal_stuff.item_checklist.ui.component;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.item_checklist.R;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistProgress;
import m.co.rh.id.a_personal_stuff.item_checklist.model.ItemChecklistState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemChecklistItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = ItemChecklistItemSV.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient RxDisposer mRxDisposer;
    /**
     * The currently shown compact-mode overflow popup, if any. Kept so it can
     * be dismissed when this card's view tree detaches (BACK navigation) or
     * when this SV is disposed — the popup lives in its own window and would
     * otherwise stay floating above the resumed screen until an outside tap.
     * Transient: ListPopupWindow is not serializable and never survives a
     * state restore.
     */
    private transient ListPopupWindow mMoreActionPopup;
    private SerialBehaviorSubject<ItemChecklistState> mItemChecklistState;
    private SerialBehaviorSubject<ItemChecklistProgress> mItemChecklistProgress;
    private DateFormat mDateFormat;

    private transient OnItemChecklistEditClicked mOnItemChecklistEditClicked;
    private transient OnItemChecklistDeleteClicked mOnItemChecklistDeleteClicked;
    private transient OnItemChecklistSelected mOnItemChecklistSelected;
    private final boolean mSelectMode;
    /**
     * Whether this row carries the designated-shopping-list cart badge.
     * Bind-time flag: the badge visibility is applied by the state
     * subscriber so a ConstraintSet switch re-applies it (applyTo resets
     * visibility from the set's snapshot), exactly like the description's
     * data-driven visibility.
     */
    private boolean mDesignated;

    /**
     * Whether this row renders the compact appearance. Reactive so a mode
     * switch re-applies the matching ConstraintSet to the existing view tree.
     */
    private final SerialBehaviorSubject<Boolean> mCompact;

    public ItemChecklistItemSV() {
        this(false, false);
    }

    public ItemChecklistItemSV(boolean selectMode, boolean compact) {
        mItemChecklistState = new SerialBehaviorSubject<>();
        mItemChecklistProgress = new SerialBehaviorSubject<>();
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        mSelectMode = selectMode;
        mCompact = new SerialBehaviorSubject<>();
        mCompact.onNext(compact);
    }

    public void setCompact(boolean compact) {
        mCompact.onNext(compact);
    }

    /**
     * Shows or hides the designated-shopping-list cart badge. The visibility
     * itself is applied by the state subscriber, so the current state is
     * re-published here — this also re-applies the badge after a ConstraintSet
     * switch and covers recycled rows without a dedicated subject.
     */
    public void setDesignated(boolean designated) {
        if (mDesignated == designated) {
            return;
        }
        mDesignated = designated;
        ItemChecklistState current = mItemChecklistState.getValue();
        if (current != null) {
            mItemChecklistState.onNext(current);
        }
    }

    public boolean isCompact() {
        Boolean value = mCompact.getValue();
        return value != null && value;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_checklist, container, false);
        CardView cardRoot = rootLayout.findViewById(R.id.card_root);
        cardRoot.setOnClickListener(this);
        TextView titleText = rootLayout.findViewById(R.id.text_title);
        TextView descriptionText = rootLayout.findViewById(R.id.text_description);
        TextView progressText = rootLayout.findViewById(R.id.text_progress);
        TextView updatedDateTimeText = rootLayout.findViewById(R.id.text_updated_date_time);
        ImageView imageDesignated = rootLayout.findViewById(R.id.image_designated);
        // The badge is GONE unless the row is designated, so it can only be
        // tapped when visible.
        imageDesignated.setOnClickListener(this);
        Button editButton = rootLayout.findViewById(R.id.button_edit);
        editButton.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);
        Button moreActionButton = rootLayout.findViewById(R.id.button_more_action);
        moreActionButton.setOnClickListener(this);
        // The overflow popup opens in its own window, so it outlives this card's
        // view tree: if BACK (or any navigation) detaches the page while it is
        // open, it keeps floating above the resumed screen until an outside tap
        // (orphaned-popup defect). Dismiss it when this card detaches;
        // dispose() covers the teardown path where no detach happens first.
        rootLayout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                // nothing to do
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                dismissMoreActionPopup();
            }
        });

        if (mSelectMode) {
            // The overflow only offers edit/delete, which make no sense
            // mid-selection, so select mode hides it along with the direct
            // buttons (the compact-mode subscription below re-applies this).
            editButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);
            moreActionButton.setVisibility(View.GONE);
        }

        ConstraintLayout constraintRoot = rootLayout.findViewById(R.id.constraint_root);
        ConstraintSet compactSet = new ConstraintSet();
        compactSet.load(activity, R.xml.item_checklist_compact_constraints);
        ConstraintSet detailedSet = new ConstraintSet();
        detailedSet.clone(constraintRoot);

        mRxDisposer.add("createView_onCompactChanged",
                mCompact.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(compact -> {
                            (compact ? compactSet : detailedSet).applyTo(constraintRoot);
                            // button_edit/button_delete live nested inside the action
                            // container, so ConstraintSet (which targets direct
                            // children) can't reliably change their visibility — do it
                            // here. In compact mode only the overflow button shows
                            // (mirroring ItemItemSV); detailed keeps the two direct
                            // buttons and never shows the overflow.
                            if (mSelectMode) {
                                // The overflow only offers edit/delete, which make no
                                // sense mid-selection, so it stays hidden in both
                                // view modes.
                                editButton.setVisibility(View.GONE);
                                deleteButton.setVisibility(View.GONE);
                                moreActionButton.setVisibility(View.GONE);
                            } else {
                                int secondaryVisibility = compact ? View.GONE : View.VISIBLE;
                                editButton.setVisibility(secondaryVisibility);
                                deleteButton.setVisibility(secondaryVisibility);
                                moreActionButton.setVisibility(
                                        compact ? View.VISIBLE : View.GONE);
                            }
                            // Re-publish so data-driven visibility (description shown
                            // when non-empty, designated badge) is re-applied after
                            // applyTo resets views from the set's snapshot.
                            ItemChecklistState current = mItemChecklistState.getValue();
                            if (current != null) {
                                mItemChecklistState.onNext(current);
                            }
                        }));

        mRxDisposer.add("createView_onItemChecklistStateChanged",
                mItemChecklistState.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemChecklistState -> {
                            titleText.setText(itemChecklistState.getTitle());
                            // data-driven badge visibility, re-applied after
                            // every applyTo (a ConstraintSet switch resets
                            // visibility from the set's snapshot)
                            imageDesignated.setVisibility(
                                    mDesignated ? View.VISIBLE : View.GONE);
                            if (!isCompact()) {
                                String description = itemChecklistState.getDescription();
                                if (description != null && !description.isEmpty()) {
                                    descriptionText.setText(description);
                                    descriptionText.setVisibility(View.VISIBLE);
                                } else {
                                    descriptionText.setText(null);
                                    descriptionText.setVisibility(View.GONE);
                                }
                            }
                            Date updatedDateTime = itemChecklistState.getUpdatedDateTime();
                            if (updatedDateTime != null) {
                                updatedDateTimeText.setText(activity.getString(R.string.updated_date_time,
                                        mDateFormat.format(updatedDateTime)));
                            } else {
                                updatedDateTimeText.setText("");
                            }
                        }));

        mRxDisposer.add("createView_onItemChecklistProgressChanged",
                mItemChecklistProgress.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemChecklistProgress -> {
                            if (itemChecklistProgress != null && itemChecklistProgress.total > 0) {
                                progressText.setText(activity.getString(R.string.checklist_progress,
                                        itemChecklistProgress.checked, itemChecklistProgress.total));
                            } else {
                                progressText.setText(activity.getString(R.string.checklist_progress, 0, 0));
                            }
                        }));

        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        dismissMoreActionPopup();
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (mSelectMode && id == R.id.card_root) {
            if (mOnItemChecklistSelected != null) {
                mOnItemChecklistSelected.itemChecklistItemSV_onItemChecklistSelected(mItemChecklistState.getValue());
            }
        } else if (!mSelectMode && (id == R.id.card_root || id == R.id.button_edit)) {
            if (mOnItemChecklistEditClicked != null) {
                mOnItemChecklistEditClicked.itemChecklistItemSV_onItemChecklistEditClicked(mItemChecklistState.getValue());
            }
        } else if (id == R.id.button_delete) {
            if (mOnItemChecklistDeleteClicked != null) {
                mOnItemChecklistDeleteClicked.itemChecklistItemSV_onItemChecklistDeleteClicked(mItemChecklistState.getValue());
            }
        } else if (id == R.id.button_more_action) {
            // Unreachable in select mode: the overflow is hidden there
            // (it only offers edit/delete).
            showMoreActionList(view);
        } else if (id == R.id.image_designated) {
            // Explanatory toast via the module's ILogger (shown as a toast by
            // the app's ToastLogger); complements the badge's
            // contentDescription.
            mLogger.i(TAG, view.getContext().getString(R.string.designated_shopping_list_hint));
        }
    }

    /**
     * Compact-mode overflow popup, anchored at the ⋮ button. Same mechanism as
     * the item card's overflow (ItemItemSV.showMoreActionList): a
     * {@link ListPopupWindow} of text rows dropping down below the anchor,
     * start-aligned, sized to the widest label. Only Edit/Delete are offered
     * and they route through the same callbacks as the detailed-mode direct
     * buttons, so no business logic is duplicated.
     */
    private void showMoreActionList(View anchor) {
        Context context = anchor.getContext();
        String[] items = new String[]{
                context.getString(R.string.menu_edit),
                context.getString(R.string.menu_delete),
        };
        ListPopupWindow listPopupWindow = new ListPopupWindow(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, items);
        listPopupWindow.setAdapter(adapter);
        listPopupWindow.setAnchorView(anchor);
        // Drop down below the anchor, start-aligned — matches the item card's
        // overflow popup.
        listPopupWindow.setDropDownGravity(android.view.Gravity.START | android.view.Gravity.TOP);
        listPopupWindow.setHeight(ListPopupWindow.WRAP_CONTENT);
        // Measure the widest text row so the popup fits the longest label
        // (simple_list_item_1 is match_parent-width, which the ListPopupWindow
        // internal measure pass collapses to ~0; setContentWidth forces a sane
        // width) — same reason as the item card's overflow popup.
        listPopupWindow.setContentWidth(measureMaxContentWidth(context, adapter));
        listPopupWindow.setOnItemClickListener((parent, view, position, id) -> {
            if (position == 0) {
                if (mOnItemChecklistEditClicked != null) {
                    mOnItemChecklistEditClicked.itemChecklistItemSV_onItemChecklistEditClicked(
                            mItemChecklistState.getValue());
                }
            } else if (position == 1) {
                if (mOnItemChecklistDeleteClicked != null) {
                    mOnItemChecklistDeleteClicked.itemChecklistItemSV_onItemChecklistDeleteClicked(
                            mItemChecklistState.getValue());
                }
            }
            listPopupWindow.dismiss();
            mMoreActionPopup = null;
        });
        mMoreActionPopup = listPopupWindow;
        listPopupWindow.show();
    }

    /**
     * Dismisses the shown overflow popup, if any. Called when this card's view
     * tree detaches from the window (e.g. BACK pops the page while the popup
     * is open) and from {@link #dispose(Activity)}: the popup lives in its own
     * window, so without this it stays floating above whatever screen is
     * resumed, orphaned, until an outside tap. Dismissing inside the detach
     * callback is safe — the popup is a separate window from this view tree.
     */
    private void dismissMoreActionPopup() {
        if (mMoreActionPopup != null) {
            if (mMoreActionPopup.isShowing()) {
                mMoreActionPopup.dismiss();
            }
            mMoreActionPopup = null;
        }
    }

    private static int measureMaxContentWidth(Context context, ArrayAdapter<String> adapter) {
        int maxWidth = 0;
        View measureView = null;
        for (int i = 0; i < adapter.getCount(); i++) {
            measureView = adapter.getView(i, measureView, null);
            // getView inflates simple_list_item_1 with a null parent, so the view
            // has no LayoutParams — TextView.checkForRelayout() crashes on setText
            // without one. Assign one before measuring.
            if (measureView.getLayoutParams() == null) {
                measureView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            measureView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int w = measureView.getMeasuredWidth();
            if (w > maxWidth) {
                maxWidth = w;
            }
        }
        // Add horizontal padding so labels aren't flush against the popup edges.
        float density = context.getResources().getDisplayMetrics().density;
        return maxWidth + (int) (32 * density);
    }

    public void setItemChecklistState(ItemChecklistState itemChecklistState) {
        mItemChecklistState.onNext(itemChecklistState);
    }

    public void setItemChecklistProgress(ItemChecklistProgress itemChecklistProgress) {
        mItemChecklistProgress.onNext(itemChecklistProgress);
    }

    public void setOnItemChecklistEditClicked(OnItemChecklistEditClicked onItemChecklistEditClicked) {
        mOnItemChecklistEditClicked = onItemChecklistEditClicked;
    }

    public void setOnItemChecklistDeleteClicked(OnItemChecklistDeleteClicked onItemChecklistDeleteClicked) {
        mOnItemChecklistDeleteClicked = onItemChecklistDeleteClicked;
    }

    public void setOnItemChecklistSelected(OnItemChecklistSelected onItemChecklistSelected) {
        mOnItemChecklistSelected = onItemChecklistSelected;
    }

    public ItemChecklistState getItemChecklistState() {
        return mItemChecklistState.getValue();
    }

    public interface OnItemChecklistEditClicked {
        void itemChecklistItemSV_onItemChecklistEditClicked(ItemChecklistState itemChecklistState);
    }

    public interface OnItemChecklistDeleteClicked {
        void itemChecklistItemSV_onItemChecklistDeleteClicked(ItemChecklistState itemChecklistState);
    }

    public interface OnItemChecklistSelected {
        void itemChecklistItemSV_onItemChecklistSelected(ItemChecklistState itemChecklistState);
    }
}
