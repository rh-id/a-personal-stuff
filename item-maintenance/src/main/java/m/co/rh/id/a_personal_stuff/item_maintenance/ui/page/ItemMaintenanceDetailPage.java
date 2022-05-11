package m.co.rh.id.a_personal_stuff.item_maintenance.ui.page;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.base.ui.component.adapter.SuggestionAdapter;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.ImageSV;
import m.co.rh.id.a_personal_stuff.item_maintenance.R;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.DeleteItemMaintenanceImageCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.NewItemMaintenanceCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.NewItemMaintenanceImageCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.QueryItemMaintenanceCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.command.UpdateItemMaintenanceCmd;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceFileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class ItemMaintenanceDetailPage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnRequestPermissionResult<Activity>, NavOnActivityResult<Activity>, Toolbar.OnMenuItemClickListener, View.OnClickListener {
    private static final String TAG = ItemMaintenanceDetailPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient ExecutorService mExecutorService;
    private transient ItemMaintenanceFileHelper mItemMaintenanceFileHelper;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient RxDisposer mRxDisposer;
    private transient NewItemMaintenanceCmd mNewItemMaintenanceCmd;
    private transient NewItemMaintenanceImageCmd mNewItemMaintenanceImageCmd;
    private transient DeleteItemMaintenanceImageCmd mDeleteItemMaintenanceImageCmd;
    private transient QueryItemMaintenanceCmd mQueryItemMaintenanceCmd;

    private transient TextWatcher mMaintenanceDateTimeTextWatcher;
    private transient TextWatcher mCostTextWatcher;
    private transient TextWatcher mDescriptionTextWatcher;
    private DateFormat mDateFormat;
    private transient Function<String, Collection<String>> mSuggestionDescriptionQuery;

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ImageSV mImageSV;

    private ItemMaintenanceState mItemMaintenanceState;

    public ItemMaintenanceDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_item_maintenance_detail);
        mImageSV = new ImageSV();
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mItemMaintenanceFileHelper = mSvProvider.get(ItemMaintenanceFileHelper.class);
        mNavExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        if (isUpdate()) {
            mNewItemMaintenanceCmd = mSvProvider.get(UpdateItemMaintenanceCmd.class);
        } else {
            mNewItemMaintenanceCmd = mSvProvider.get(NewItemMaintenanceCmd.class);
        }
        mNewItemMaintenanceImageCmd = mSvProvider.get(NewItemMaintenanceImageCmd.class);
        mDeleteItemMaintenanceImageCmd = mSvProvider.get(DeleteItemMaintenanceImageCmd.class);
        mQueryItemMaintenanceCmd = mSvProvider.get(QueryItemMaintenanceCmd.class);
        mMaintenanceDateTimeTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                mNewItemMaintenanceCmd.valid(mItemMaintenanceState);
            }
        };
        mCostTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String s = editable.toString();
                BigDecimal cost = null;
                try {
                    if (s.isEmpty()) {
                        cost = BigDecimal.ZERO;
                    } else {
                        cost = new BigDecimal(s);
                    }
                } catch (Throwable throwable) {
                    // Leave blank
                }
                mItemMaintenanceState.setItemMaintenanceCost(cost);
                mNewItemMaintenanceCmd.valid(mItemMaintenanceState);
            }
        };
        mDescriptionTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // Leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String s = editable.toString();
                mItemMaintenanceState.setItemMaintenanceDescription(s);
                mNewItemMaintenanceCmd.valid(mItemMaintenanceState);
            }
        };
        mSuggestionDescriptionQuery = s -> mQueryItemMaintenanceCmd.searchItemMaintenanceDescription(s).blockingGet();
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        if (isUpdate()) {
            mItemMaintenanceState = getItemMaintenanceState();
        } else {
            mItemMaintenanceState = new ItemMaintenanceState();
            mItemMaintenanceState.setItemMaintenanceItemId(getItemId());
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_maintenance_detail, container, false);
        EditText inputMaintenanceDateTime = rootLayout.findViewById(R.id.input_text_maintenance_date_time);
        inputMaintenanceDateTime.setOnClickListener(this);
        inputMaintenanceDateTime.addTextChangedListener(mMaintenanceDateTimeTextWatcher);
        EditText inputCost = rootLayout.findViewById(R.id.input_text_cost);
        inputCost.setOnClickListener(this);
        inputCost.addTextChangedListener(mCostTextWatcher);
        AutoCompleteTextView inputDescription = rootLayout.findViewById(R.id.input_text_description);
        inputDescription.setThreshold(1);
        inputDescription.setAdapter(new SuggestionAdapter
                (activity, mSuggestionDescriptionQuery));
        inputDescription.addTextChangedListener(mDescriptionTextWatcher);
        ViewGroup containerImageComponent = rootLayout.findViewById(R.id.container_image_component);
        containerImageComponent.addView(mImageSV.buildView(activity, containerImageComponent));
        if (isUpdate()) {
            mAppBarSV.setTitle(activity.getString(R.string.title_update_item_maintenance));
        } else {
            mAppBarSV.setTitle(activity.getString(R.string.title_add_item_maintenance));
        }
        mAppBarSV.setMenuItemClick(this);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, containerAppBar));
        mRxDisposer.add("createView_onItemMaintenanceChanged",
                mItemMaintenanceState.getItemMaintenanceFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemMaintenance -> {
                            if (itemMaintenance.maintenanceDateTime != null) {
                                inputMaintenanceDateTime.setText(mDateFormat.format(itemMaintenance.maintenanceDateTime));
                            } else {
                                inputMaintenanceDateTime.setText(null);
                            }
                            if (itemMaintenance.cost != null) {
                                inputCost.setText(itemMaintenance.cost.toString());
                            } else {
                                inputCost.setText(null);
                            }
                            inputDescription.setText(itemMaintenance.description);
                        }));
        mRxDisposer.add("createView_onItemUsageImagesChanged",
                mItemMaintenanceState.getItemMaintenanceImagesFlow()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(itemMaintenanceImages -> {
                            if (!itemMaintenanceImages.isEmpty()) {
                                List<File> fileList = new ArrayList<>();
                                for (ItemMaintenanceImage itemUsageImage : itemMaintenanceImages) {
                                    fileList.add(mItemMaintenanceFileHelper.getItemMaintenanceImage(itemUsageImage.fileName));
                                }
                                mImageSV.setImageFiles(fileList);
                            }
                        }));
        mRxDisposer.add("createView_onImageSV_deletedFile",
                mImageSV.getDeletedFileSubject()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(imageFile -> {
                            String imageFileName = imageFile.getName();
                            ItemMaintenanceImage deletedItemImage = null;
                            List<ItemMaintenanceImage> itemMaintenanceImageList = mItemMaintenanceState.getItemMaintenanceImages();
                            for (ItemMaintenanceImage itemMaintenanceImage : itemMaintenanceImageList) {
                                if (imageFileName.equals(itemMaintenanceImage.fileName)) {
                                    deletedItemImage = itemMaintenanceImage;
                                }
                            }
                            if (deletedItemImage != null) {
                                itemMaintenanceImageList.remove(deletedItemImage);
                                if (isUpdate()) {
                                    mRxDisposer.add("createView_onImageSV_deletedFile_deleteItemMaintenanceImage",
                                            mDeleteItemMaintenanceImageCmd.execute(deletedItemImage)
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe((itemImages, throwable) -> {
                                                        if (throwable != null) {
                                                            Throwable cause = throwable.getCause();
                                                            if (cause == null) cause = throwable;
                                                            mLogger.e(TAG, cause.getMessage(), cause);
                                                        } else {
                                                            mLogger.i(TAG,
                                                                    mSvProvider.getContext()
                                                                            .getString(R.string.success_delete_item_maintenance_image));
                                                        }
                                                    })
                                    );
                                }
                            }
                        }));
        mRxDisposer.add("createView_onImageSV_addFile",
                mImageSV.getAddFileSubject()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(imageFile -> {
                            String imageFileName = imageFile.getName();
                            Future<File> itemMaintenanceImageFile = mExecutorService.submit(() ->
                                    mItemMaintenanceFileHelper.createItemMaintenanceImage(Uri.fromFile(imageFile),
                                            imageFileName));
                            Future<File> itemMaintenanceImageThumbnail = mExecutorService.submit(() ->
                                    mItemMaintenanceFileHelper.createItemMaintenanceImageThumbnail(Uri.fromFile(imageFile),
                                            imageFileName));
                            try {
                                itemMaintenanceImageThumbnail.get();
                                File addImageFile = itemMaintenanceImageFile.get();
                                ItemMaintenanceImage itemMaintenanceImage = new ItemMaintenanceImage();
                                itemMaintenanceImage.fileName = addImageFile.getName();
                                if (isUpdate()) {
                                    itemMaintenanceImage.itemMaintenanceId = mItemMaintenanceState.getItemMaintenanceId();
                                    itemMaintenanceImage = mNewItemMaintenanceImageCmd.execute(itemMaintenanceImage).blockingGet();
                                    mLogger.i(TAG, mSvProvider.getContext()
                                            .getString(R.string.success_add_item_maintenance_image));
                                }
                                List<ItemMaintenanceImage> itemMaintenanceImageList = mItemMaintenanceState.getItemMaintenanceImages();
                                itemMaintenanceImageList.add(itemMaintenanceImage);
                                mImageSV.addImage(addImageFile);
                            } catch (Throwable throwable) {
                                mLogger.e(TAG, throwable.getMessage(), throwable);
                            }
                        }));
        mRxDisposer.add("createView_onItemMaintenanceDateTimeValid",
                mNewItemMaintenanceCmd.getMaintenanceDateTimeValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputMaintenanceDateTime.setError(s);
                            } else {
                                inputMaintenanceDateTime.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onItemMaintenanceDescriptionValid",
                mNewItemMaintenanceCmd.getDescriptionValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputDescription.setError(s);
                            } else {
                                inputDescription.setError(null);
                            }
                        }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mImageSV.dispose(activity);
        mImageSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.input_text_maintenance_date_time) {
            mNavigator.push(mNavExtDialogConfig.route_dateTimePickerDialog(),
                    mNavExtDialogConfig.args_dateTimePickerDialog(true, mItemMaintenanceState.getItemMaintenanceDateTime()),
                    (navigator, navRoute, activity, currentView) -> maintenanceDateTimeSelected(navRoute));
        }
    }

    private void maintenanceDateTimeSelected(NavRoute navRoute) {
        Date result = mNavExtDialogConfig.result_dateTimePickerDialog(navRoute);
        if (result != null) {
            mItemMaintenanceState.updateItemMaintenanceDateTime(result);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            if (mNewItemMaintenanceCmd.valid(mItemMaintenanceState)) {
                mRxDisposer.add("onMenuItemClick_save",
                        mNewItemMaintenanceCmd.execute(mItemMaintenanceState)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((itemMaintenanceState, throwable) -> {
                                    if (throwable != null) {
                                        Throwable cause = throwable.getCause();
                                        if (cause == null) cause = throwable;
                                        mLogger.e(TAG, cause.getMessage(), cause);
                                    } else {
                                        mLogger.i(TAG, mSvProvider.getContext()
                                                .getString(R.string.success_add_item_maintenance));
                                        mNavigator.pop();
                                    }
                                }));
            } else {
                String error = mNewItemMaintenanceCmd.getValidationError();
                mLogger.i(TAG, error);
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity, INavigator INavigator, int requestCode, String[] permissions, int[] grantResults) {
        mImageSV.onRequestPermissionsResult(currentView, activity, INavigator, requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator INavigator, int requestCode, int resultCode, Intent data) {
        mImageSV.onActivityResult(currentView, activity, INavigator, requestCode, resultCode, data);
    }

    private Long getItemId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemId;
        }
        return null;
    }

    private ItemMaintenanceState getItemMaintenanceState() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemMaintenanceState;
        }
        return null;
    }

    private boolean isUpdate() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemMaintenanceState != null;
        }
        return false;
    }

    public static class Args implements Serializable {
        public static Args with(ItemMaintenanceState itemMaintenanceState) {
            Args args = new Args();
            args.itemMaintenanceState = itemMaintenanceState;
            return args;
        }

        public static Args with(long itemId) {
            Args args = new Args();
            args.itemId = itemId;
            return args;
        }

        static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable args = navRoute.getRouteArgs();
                if (args instanceof Args) {
                    return (Args) args;
                }
            }
            return null;
        }

        private Long itemId;
        private ItemMaintenanceState itemMaintenanceState;
    }
}
