package m.co.rh.id.a_personal_stuff.item_checklist.ui.component;

import android.app.Activity;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.item_checklist.R;
import m.co.rh.id.a_personal_stuff.item_checklist.entity.ItemChecklistItem;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemChecklistEntrySV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private SerialBehaviorSubject<ItemChecklistEntry> mItemChecklistEntry;
    private DateFormat mDateFormat;

    private transient OnItemChecklistEntryDeleteClicked mOnItemChecklistEntryDeleteClicked;
    private transient OnItemChecklistEntryCheckClicked mOnItemChecklistEntryCheckClicked;

    public ItemChecklistEntrySV() {
        mItemChecklistEntry = new SerialBehaviorSubject<>();
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_checklist_entry, container, false);
        CardView cardRoot = rootLayout.findViewById(R.id.card_root);
        cardRoot.setOnClickListener(this);
        TextView nameText = rootLayout.findViewById(R.id.text_name);
        TextView amountText = rootLayout.findViewById(R.id.text_amount);
        MaterialCheckBox checkBox = rootLayout.findViewById(R.id.button_check);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);

        mRxDisposer.add("createView_onItemChecklistEntryChanged",
                mItemChecklistEntry.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(entry -> {
                            if (entry != null && entry.itemState != null) {
                                nameText.setText(entry.itemState.getItemName());
                                int amount = entry.itemState.getItemAmount();
                                if (amount > 0) {
                                    amountText.setText("×" + amount);
                                    amountText.setVisibility(View.VISIBLE);
                                } else {
                                    amountText.setVisibility(View.GONE);
                                }

                                // Apply checked state styling
                                boolean isChecked = entry.itemChecklistItem != null &&
                                        entry.itemChecklistItem.checkedDateTime != null;
                                checkBox.setChecked(isChecked);
                                nameText.setPaintFlags(isChecked ?
                                        nameText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG :
                                        nameText.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                                nameText.setAlpha(isChecked ? 0.5f : 1.0f);
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
        if (id == R.id.card_root) {
            if (mOnItemChecklistEntryCheckClicked != null) {
                mOnItemChecklistEntryCheckClicked.itemChecklistEntrySV_onItemChecklistEntryCheckClicked(mItemChecklistEntry.getValue());
            }
        } else if (id == R.id.button_delete) {
            if (mOnItemChecklistEntryDeleteClicked != null) {
                mOnItemChecklistEntryDeleteClicked.itemChecklistEntrySV_onItemChecklistEntryDeleteClicked(mItemChecklistEntry.getValue());
            }
        }
    }

    public void setItemChecklistEntry(ItemChecklistEntry itemChecklistEntry) {
        mItemChecklistEntry.onNext(itemChecklistEntry);
    }

    public void setOnItemChecklistEntryDeleteClicked(OnItemChecklistEntryDeleteClicked onItemChecklistEntryDeleteClicked) {
        mOnItemChecklistEntryDeleteClicked = onItemChecklistEntryDeleteClicked;
    }

    public void setOnItemChecklistEntryCheckClicked(OnItemChecklistEntryCheckClicked onItemChecklistEntryCheckClicked) {
        mOnItemChecklistEntryCheckClicked = onItemChecklistEntryCheckClicked;
    }

    public ItemChecklistEntry getItemChecklistEntry() {
        return mItemChecklistEntry.getValue();
    }

    public interface OnItemChecklistEntryDeleteClicked {
        void itemChecklistEntrySV_onItemChecklistEntryDeleteClicked(ItemChecklistEntry itemChecklistEntry);
    }

    public interface OnItemChecklistEntryCheckClicked {
        void itemChecklistEntrySV_onItemChecklistEntryCheckClicked(ItemChecklistEntry itemChecklistEntry);
    }

    public static class ItemChecklistEntry implements Serializable, Cloneable {
        public ItemChecklistItem itemChecklistItem;
        public ItemState itemState;

        public ItemChecklistEntry(ItemChecklistItem itemChecklistItem, ItemState itemState) {
            this.itemChecklistItem = itemChecklistItem;
            this.itemState = itemState;
        }
    }
}
