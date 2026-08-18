package m.co.rh.id.a_personal_stuff.item_checklist.ui.component;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemChecklistItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private SerialBehaviorSubject<ItemChecklistState> mItemChecklistState;
    private SerialBehaviorSubject<ItemChecklistProgress> mItemChecklistProgress;
    private DateFormat mDateFormat;

    private transient OnItemChecklistEditClicked mOnItemChecklistEditClicked;
    private transient OnItemChecklistDeleteClicked mOnItemChecklistDeleteClicked;
    private transient OnItemChecklistSelected mOnItemChecklistSelected;
    private final boolean mSelectMode;

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

    public boolean isCompact() {
        Boolean value = mCompact.getValue();
        return value != null && value;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
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
        Button editButton = rootLayout.findViewById(R.id.button_edit);
        editButton.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);

        if (mSelectMode) {
            editButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);
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
                            // Re-publish so data-driven visibility (description shown
                            // when non-empty) is re-applied after applyTo resets views
                            // from the set's snapshot.
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
        }
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
