package m.co.rh.id.a_personal_stuff.item_maintenance.ui.component;

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
import m.co.rh.id.a_personal_stuff.item_maintenance.R;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemMaintenanceItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;

    private SerialBehaviorSubject<ItemMaintenanceState> mItemMaintenanceState;
    private DateFormat mDateFormat;

    private transient OnItemMaintenanceEditClicked mOnItemMaintenanceEditClicked;
    private transient OnItemMaintenanceDeleteClicked mOnItemMaintenanceDeleteClicked;

    public ItemMaintenanceItemSV() {
        mItemMaintenanceState = new SerialBehaviorSubject<>();
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_maintenance_item, container, false);
        TextView maintenanceDateTimeText = rootLayout.findViewById(R.id.text_maintenance_date_time);
        TextView costText = rootLayout.findViewById(R.id.text_cost);
        TextView descriptionText = rootLayout.findViewById(R.id.text_description);
        Button editButton = rootLayout.findViewById(R.id.button_edit);
        editButton.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);
        Context context = activity.getApplicationContext();
        mRxDisposer.add("createView_onItemMaintenanceStateChanged",
                mItemMaintenanceState.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemMaintenanceState -> {
                            maintenanceDateTimeText.setText(mDateFormat.format(itemMaintenanceState.getItemMaintenanceDateTime()));
                            costText.setText(
                                    context
                                            .getString(R.string.cost_, itemMaintenanceState.getItemMaintenanceCost())
                            );
                            String description = itemMaintenanceState.getItemMaintenanceDescription();
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
            if (mOnItemMaintenanceEditClicked != null) {
                mOnItemMaintenanceEditClicked.itemMaintenanceItemSV_onItemMaintenanceEditClicked(mItemMaintenanceState.getValue());
            }
        } else if (id == R.id.button_delete) {
            if (mOnItemMaintenanceDeleteClicked != null) {
                mOnItemMaintenanceDeleteClicked.itemMaintenanceItemSV_onItemMaintenanceDeleteClicked(mItemMaintenanceState.getValue());
            }
        }
    }

    public void setItemMaintenanceState(ItemMaintenanceState itemMaintenanceState) {
        mItemMaintenanceState.onNext(itemMaintenanceState);
    }

    public void setOnItemMaintenanceEditClicked(OnItemMaintenanceEditClicked onItemMaintenanceDeleteClicked) {
        mOnItemMaintenanceEditClicked = onItemMaintenanceDeleteClicked;
    }

    public void setOnItemMaintenanceDeleteClicked(OnItemMaintenanceDeleteClicked onItemMaintenanceDeleteClicked) {
        mOnItemMaintenanceDeleteClicked = onItemMaintenanceDeleteClicked;
    }

    public ItemMaintenanceState getItemMaintenanceState() {
        return mItemMaintenanceState.getValue();
    }

    public interface OnItemMaintenanceEditClicked {
        void itemMaintenanceItemSV_onItemMaintenanceEditClicked(ItemMaintenanceState itemMaintenanceState);
    }

    public interface OnItemMaintenanceDeleteClicked {
        void itemMaintenanceItemSV_onItemMaintenanceDeleteClicked(ItemMaintenanceState itemMaintenanceState);
    }
}
