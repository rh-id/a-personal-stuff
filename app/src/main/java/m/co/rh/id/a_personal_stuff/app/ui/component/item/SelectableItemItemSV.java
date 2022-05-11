package m.co.rh.id.a_personal_stuff.app.ui.component.item;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class SelectableItemItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;

    private final SerialBehaviorSubject<ItemState> mItemState;
    private final DateFormat mDateFormat;
    private final SerialBehaviorSubject<Boolean> mSelected;

    private transient OnItemSelected mOnItemSelected;

    public SelectableItemItemSV() {
        mItemState = new SerialBehaviorSubject<>();
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
        mSelected = new SerialBehaviorSubject<>(false);
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.selectable_item_item, container, false);
        rootLayout.setOnClickListener(this);
        TextView expireDateTimeText = rootLayout.findViewById(R.id.text_expired_date_time);
        TextView nameText = rootLayout.findViewById(R.id.text_name);
        RadioButton radioSelectButton = rootLayout.findViewById(R.id.button_radio_select);
        mRxDisposer.add("createView_onSelected",
                mSelected.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(radioSelectButton::setChecked));
        mRxDisposer.add("createView_onItemStateChanged",
                mItemState.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemState -> {
                            Date expiredDateTime = itemState.getItemExpiredDateTime();
                            if (expiredDateTime != null) {
                                expireDateTimeText.setText(mDateFormat.format(expiredDateTime));
                                expireDateTimeText.setVisibility(View.VISIBLE);
                            } else {
                                expireDateTimeText.setText(null);
                                expireDateTimeText.setVisibility(View.GONE);
                            }
                            nameText.setText(itemState.getItemName());
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
        boolean selected = !mSelected.getValue();
        mSelected.onNext(selected);
        if (mOnItemSelected != null) {
            mOnItemSelected.selectableItemItemSv_onItemSelected(mItemState.getValue(), selected);
        }
    }

    public void setItemState(ItemState itemState) {
        mItemState.onNext(itemState);
    }

    public ItemState getItemState() {
        return mItemState.getValue();
    }

    public void updateSelected(boolean selected) {
        if (!mSelected.getValue().equals(selected)) {
            mSelected.onNext(selected);
        }
    }

    public void setOnItemSelected(OnItemSelected onItemSelected) {
        mOnItemSelected = onItemSelected;
    }

    public void setSelected(boolean selected) {
        mSelected.onNext(selected);
    }

    public interface OnItemSelected {
        void selectableItemItemSv_onItemSelected(ItemState itemState, boolean selected);
    }
}
