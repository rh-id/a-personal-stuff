package m.co.rh.id.a_personal_stuff.item_reminder.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import com.google.android.material.chip.Chip;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.item_reminder.R;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemReminderItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;

    private SerialBehaviorSubject<ItemReminder> mItemReminder;
    /** Nullable — the owner item's name, shown in global mode only. */
    private String mItemName;
    private DateFormat mDateFormat;

    /**
     * Whether this row renders the compact appearance. Reactive so a mode
     * switch re-applies the matching ConstraintSet to the existing view tree.
     */
    private final SerialBehaviorSubject<Boolean> mCompact;

    private transient OnItemReminderEditClicked mOnItemReminderEditClicked;
    private transient OnItemReminderDeleteClicked mOnItemReminderDeleteClicked;
    private transient OnItemNameChipClicked mOnItemNameChipClicked;

    public ItemReminderItemSV() {
        this(false);
    }

    public ItemReminderItemSV(boolean compact) {
        mItemReminder = new SerialBehaviorSubject<>();
        mCompact = new SerialBehaviorSubject<>();
        mCompact.onNext(compact);
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
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
        // Single unified layout; compact vs detailed is a ConstraintSet applied
        // to this same view tree, not a different inflation.
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_reminder_item, container, false);
        rootLayout.setOnClickListener(this);
        // ConstraintSet operates on the ConstraintLayout child, not the CardView root.
        ConstraintLayout constraintRoot =
                rootLayout.findViewById(R.id.constraint_root);
        TextView reminderDateTimeText = rootLayout.findViewById(R.id.text_reminder_date_time);
        Chip itemChip = rootLayout.findViewById(R.id.chip_item_name);
        TextView messageText = rootLayout.findViewById(R.id.text_message);
        Button editButton = rootLayout.findViewById(R.id.button_edit);
        editButton.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);

        ConstraintSet compactSet = new ConstraintSet();
        compactSet.load(activity, R.xml.item_reminder_item_compact_constraints);
        // Clone the detailed constraints from the inflated ConstraintLayout child
        // (the layout root is a CardView, so cloning the live view is unambiguous).
        ConstraintSet detailedSet = new ConstraintSet();
        detailedSet.clone(constraintRoot);
        mRxDisposer.add("createView_onCompactChanged",
                mCompact.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(compact -> {
                            (compact ? compactSet : detailedSet).applyTo(constraintRoot);
                            // button_edit is nested inside the action container, so
                            // ConstraintSet (which targets direct children) can't
                            // reliably toggle it — do it here. In compact mode the
                            // edit button is hidden (edit is via card tap); delete stays.
                            editButton.setVisibility(compact ? View.GONE : View.VISIBLE);
                            // Re-publish so any data-driven field state is re-applied
                            // after applyTo resets views from the set's snapshot.
                            ItemReminder current = mItemReminder.getValue();
                            if (current != null) {
                                mItemReminder.onNext(current);
                            }
                        }));

        mRxDisposer.add("createView_onItemReminderChanged",
                mItemReminder.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemReminder -> {
                            reminderDateTimeText.setText(mDateFormat.format(itemReminder.reminderDateTime));
                            // the owner chip is present in global mode only; GONE
                            // keeps per-item rows (and orphaned reminders)
                            // identical to a missing view. The click listener is
                            // attached only when a name is bound.
                            if (mItemName != null) {
                                itemChip.setText(mItemName);
                                itemChip.setVisibility(View.VISIBLE);
                                itemChip.setOnClickListener(this);
                            } else {
                                itemChip.setOnClickListener(null);
                                itemChip.setVisibility(View.GONE);
                            }
                            messageText.setText(itemReminder.message);
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
        if (id == R.id.card_root || id == R.id.button_edit) {
            if (mOnItemReminderEditClicked != null) {
                mOnItemReminderEditClicked
                        .itemReminderItemSV_onItemReminderEditClicked(mItemReminder.getValue());
            }
        } else if (id == R.id.button_delete) {
            if (mOnItemReminderDeleteClicked != null) {
                mOnItemReminderDeleteClicked
                        .itemReminderItemSV_onItemReminderDeleteClicked(mItemReminder.getValue());
            }
        } else if (id == R.id.chip_item_name) {
            if (mOnItemNameChipClicked != null) {
                mOnItemNameChipClicked
                        .itemReminderItemSV_onItemNameChipClicked(mItemReminder.getValue());
            }
        }
    }

    public void setOnItemReminderEditClicked(OnItemReminderEditClicked onItemReminderEditClicked) {
        mOnItemReminderEditClicked = onItemReminderEditClicked;
    }

    public void setOnItemReminderDeleteClicked(OnItemReminderDeleteClicked onItemReminderDeleteClicked) {
        mOnItemReminderDeleteClicked = onItemReminderDeleteClicked;
    }

    public void setOnItemNameChipClicked(OnItemNameChipClicked onItemNameChipClicked) {
        mOnItemNameChipClicked = onItemNameChipClicked;
    }

    public void setItemReminder(ItemReminder itemReminder, String itemName) {
        mItemName = itemName;
        mItemReminder.onNext(itemReminder);
    }

    public ItemReminder getItemReminder() {
        return mItemReminder.getValue();
    }

    public interface OnItemReminderEditClicked {
        void itemReminderItemSV_onItemReminderEditClicked(ItemReminder itemReminder);
    }

    public interface OnItemReminderDeleteClicked {
        void itemReminderItemSV_onItemReminderDeleteClicked(ItemReminder itemReminder);
    }

    public interface OnItemNameChipClicked {
        void itemReminderItemSV_onItemNameChipClicked(ItemReminder itemReminder);
    }
}
