package m.co.rh.id.a_personal_stuff.item_reminder.ui.component;

import android.app.Activity;
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
import m.co.rh.id.a_personal_stuff.item_reminder.R;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemReminderItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;

    private SerialBehaviorSubject<ItemReminder> mItemReminder;
    private DateFormat mDateFormat;

    private transient OnItemReminderDeleteClicked mOnItemReminderDeleteClicked;

    public ItemReminderItemSV() {
        mItemReminder = new SerialBehaviorSubject<>();
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_reminder_item, container, false);
        TextView reminderDateTimeText = rootLayout.findViewById(R.id.text_reminder_date_time);
        TextView messageText = rootLayout.findViewById(R.id.text_message);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);
        mRxDisposer.add("createView_onItemReminderChanged",
                mItemReminder.getSubject().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemReminder -> {
                            reminderDateTimeText.setText(mDateFormat.format(itemReminder.reminderDateTime));
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
        if (id == R.id.button_delete) {
            if (mOnItemReminderDeleteClicked != null) {
                mOnItemReminderDeleteClicked
                        .itemReminderItemSV_onItemReminderDeleteClicked(mItemReminder.getValue());
            }
        }
    }

    public void setOnItemReminderDeleteClicked(OnItemReminderDeleteClicked onItemReminderDeleteClicked) {
        mOnItemReminderDeleteClicked = onItemReminderDeleteClicked;
    }

    public void setItemReminder(ItemReminder itemReminder) {
        mItemReminder.onNext(itemReminder);
    }

    public ItemReminder getItemReminder() {
        return mItemReminder.getValue();
    }

    public interface OnItemReminderDeleteClicked {
        void itemReminderItemSV_onItemReminderDeleteClicked(ItemReminder itemReminder);
    }
}
