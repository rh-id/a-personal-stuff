package m.co.rh.id.a_personal_stuff.item_usage.ui.component;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.item_usage.R;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemUsageItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;

    private SerialBehaviorSubject<ItemUsageState> mItemUsageState;
    private DateFormat mDateFormat;

    private transient OnItemUsageEditClicked mOnItemUsageEditClicked;
    private transient OnItemUsageDeleteClicked mOnItemUsageDeleteClicked;

    public ItemUsageItemSV() {
        mItemUsageState = new SerialBehaviorSubject<>();
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_usage_item, container, false);
        TextView createdDateTimeText = rootLayout.findViewById(R.id.text_created_date_time);
        TextView amountText = rootLayout.findViewById(R.id.text_amount);
        TextView descriptionText = rootLayout.findViewById(R.id.text_description);
        Button editButton = rootLayout.findViewById(R.id.button_edit);
        editButton.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);
        Context context = activity.getApplicationContext();
        mRxDisposer.add("createView_onItemUsageStateChanged",
                mItemUsageState.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemUsageState -> {
                            createdDateTimeText.setText(mDateFormat.format(itemUsageState.getItemUsageCreatedDateTime()));
                            amountText.setText(
                                    context
                                            .getString(R.string.amount_, itemUsageState.getItemUsageAmount())
                            );
                            String description = itemUsageState.getItemUsageDescription();
                            if (description != null && !description.isEmpty()) {
                                descriptionText.setText(
                                        context.getString(R.string.description_,
                                                description)
                                );
                                descriptionText.setVisibility(View.VISIBLE);
                            } else {
                                descriptionText.setText(null);
                                descriptionText.setVisibility(View.GONE);
                            }
                        }));
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_edit) {
            if (mOnItemUsageEditClicked != null) {
                mOnItemUsageEditClicked.itemUsageItemSV_onItemUsageEditClicked(mItemUsageState.getValue());
            }
        } else if (id == R.id.button_delete) {
            if (mOnItemUsageDeleteClicked != null) {
                mOnItemUsageDeleteClicked.itemUsageItemSV_onItemUsageDeleteClicked(mItemUsageState.getValue());
            }
        }
    }

    public void setItemUsageState(ItemUsageState itemUsageState) {
        mItemUsageState.onNext(itemUsageState);
    }

    public void setOnItemUsageEditClicked(OnItemUsageEditClicked onItemUsageEditClicked) {
        mOnItemUsageEditClicked = onItemUsageEditClicked;
    }

    public void setOnItemUsageDeleteClicked(OnItemUsageDeleteClicked onItemUsageDeleteClicked) {
        mOnItemUsageDeleteClicked = onItemUsageDeleteClicked;
    }

    public ItemUsageState getItemUsageState() {
        return mItemUsageState.getValue();
    }

    public interface OnItemUsageEditClicked {
        void itemUsageItemSV_onItemUsageEditClicked(ItemUsageState itemUsageState);
    }

    public interface OnItemUsageDeleteClicked {
        void itemUsageItemSV_onItemUsageDeleteClicked(ItemUsageState itemUsageState);
    }
}
