package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.io.Serializable;

import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.ui.component.item.ItemListSV;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemSelectPage extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    private static final String TAG = ItemSelectPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;

    private transient ILogger mLogger;

    @NavInject
    private ItemListSV mItemListSV;

    public ItemSelectPage() {
        mItemListSV = new ItemListSV(true);
    }

    @Override
    public void provideComponent(Provider provider) {
        mLogger = provider.get(ILogger.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_select, container, false);
        ViewGroup content = rootLayout.findViewById(R.id.container_content);
        content.addView(mItemListSV.buildView(activity, content));
        Button cancelButton = rootLayout.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);
        Button okButton = rootLayout.findViewById(R.id.button_ok);
        okButton.setOnClickListener(this);
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mItemListSV.dispose(activity);
        mItemListSV = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_cancel) {
            mNavigator.pop();
        } else if (id == R.id.button_ok) {
            ItemState selectedItem = mItemListSV.getSelectedItem();
            if (selectedItem != null) {
                mNavigator.pop(Result.with(selectedItem));
            } else {
                mLogger.i(TAG, view.getContext().getString(R.string.error_please_select_item));
            }
        }
    }

    public static class Result implements Serializable {
        public static Result of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable result = navRoute.getRouteResult();
                if (result instanceof Result) {
                    return (Result) result;
                }
            }
            return null;
        }

        static Result with(ItemState itemState) {
            Result result = new Result();
            result.itemState = itemState;
            return result;
        }

        private ItemState itemState;

        public ItemState getItemState() {
            return itemState;
        }
    }
}
