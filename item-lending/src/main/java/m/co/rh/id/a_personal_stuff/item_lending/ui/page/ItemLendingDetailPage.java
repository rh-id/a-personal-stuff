package m.co.rh.id.a_personal_stuff.item_lending.ui.page;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.ImageSV;
import m.co.rh.id.a_personal_stuff.item_lending.R;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLendingImage;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.DeleteItemLendingImageCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.NewItemLendingCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.NewItemLendingImageCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.command.UpdateItemLendingCmd;
import m.co.rh.id.a_personal_stuff.item_lending.provider.component.ItemLendingFileHelper;
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

public class ItemLendingDetailPage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnActivityResult<Activity>, NavOnRequestPermissionResult<Activity>, Toolbar.OnMenuItemClickListener, View.OnClickListener {
    private static final String TAG = ItemLendingDetailPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient ExecutorService mExecutorService;
    private transient ItemLendingFileHelper mItemLendingFileHelper;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient RxDisposer mRxDisposer;
    private transient NewItemLendingCmd mNewItemLendingCmd;
    private transient NewItemLendingImageCmd mNewItemLendingImageCmd;
    private transient DeleteItemLendingImageCmd mDeleteItemLendingImageCmd;

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ImageSV mImageSV;

    private ItemLendingState mItemLendingState;

    private transient TextWatcher mBorrowerNameTextWatcher;
    private transient TextWatcher mNoteTextWatcher;
    private DateFormat mDateFormat;

    public ItemLendingDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_item_lending_detail);
        mImageSV = new ImageSV();
        mImageSV.setSyncIndexOnReturn(true);
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mItemLendingFileHelper = mSvProvider.get(ItemLendingFileHelper.class);
        mNavExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        if (isUpdate()) {
            mNewItemLendingCmd = mSvProvider.get(UpdateItemLendingCmd.class);
        } else {
            mNewItemLendingCmd = mSvProvider.get(NewItemLendingCmd.class);
        }
        mNewItemLendingImageCmd = mSvProvider.get(NewItemLendingImageCmd.class);
        mDeleteItemLendingImageCmd = mSvProvider.get(DeleteItemLendingImageCmd.class);
        mBorrowerNameTextWatcher = new TextWatcher() {
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
                mItemLendingState.setBorrowerName(s);
                mNewItemLendingCmd.valid(mItemLendingState);
            }
        };
        mNoteTextWatcher = new TextWatcher() {
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
                mItemLendingState.setNote(s);
            }
        };
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        if (isUpdate()) {
            mItemLendingState = getItemLendingState();
        } else {
            mItemLendingState = new ItemLendingState();
            mItemLendingState.setItemId(getItemId());
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_lending_detail, container, false);
        EditText inputBorrowerName = rootLayout.findViewById(R.id.input_text_borrower_name);
        inputBorrowerName.addTextChangedListener(mBorrowerNameTextWatcher);
        EditText inputLendDateTime = rootLayout.findViewById(R.id.input_text_lend_date_time);
        inputLendDateTime.setOnClickListener(this);
        EditText inputDueDateTime = rootLayout.findViewById(R.id.input_text_due_date_time);
        inputDueDateTime.setOnClickListener(this);
        Button clearDueDateTimeButton = rootLayout.findViewById(R.id.button_clear_due_date_time);
        clearDueDateTimeButton.setOnClickListener(this);
        EditText inputReturnedDateTime = rootLayout.findViewById(R.id.input_text_returned_date_time);
        inputReturnedDateTime.setOnClickListener(this);
        Button clearReturnedDateTimeButton = rootLayout.findViewById(R.id.button_clear_returned_date_time);
        clearReturnedDateTimeButton.setOnClickListener(this);
        EditText inputNote = rootLayout.findViewById(R.id.input_text_note);
        inputNote.addTextChangedListener(mNoteTextWatcher);
        ViewGroup imageContainer = rootLayout.findViewById(R.id.container_image_component);
        imageContainer.addView(mImageSV.buildView(activity, imageContainer));
        if (isUpdate()) {
            mAppBarSV.setTitle(activity.getString(R.string.title_update_item_lending));
        } else {
            mAppBarSV.setTitle(activity.getString(R.string.title_add_item_lending));
        }
        mAppBarSV.setMenuItemClick(this);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSV.buildView(activity, appBarContainer));
        mRxDisposer.add("createView_onItemLendingChanged",
                mItemLendingState.getItemLendingFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemLending -> {
                            inputBorrowerName.setText(itemLending.borrowerName);
                            inputNote.setText(itemLending.note);
                            Date lendDateTime = itemLending.lendDateTime;
                            if (lendDateTime != null) {
                                inputLendDateTime.setText(mDateFormat.format(lendDateTime));
                            }
                            Date dueDateTime = itemLending.dueDateTime;
                            if (dueDateTime != null) {
                                inputDueDateTime.setText(mDateFormat.format(dueDateTime));
                            } else {
                                inputDueDateTime.setText(null);
                            }
                            Date returnedDateTime = itemLending.returnedDateTime;
                            if (returnedDateTime != null) {
                                inputReturnedDateTime.setText(mDateFormat.format(returnedDateTime));
                            } else {
                                inputReturnedDateTime.setText(null);
                            }
                        }));
        mRxDisposer.add("createView_onItemLendingImagesChanged",
                mItemLendingState.getItemLendingImagesFlow()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(itemLendingImages -> {
                            if (!itemLendingImages.isEmpty()) {
                                List<File> fileList = new ArrayList<>();
                                for (ItemLendingImage itemLendingImage : itemLendingImages) {
                                    fileList.add(mItemLendingFileHelper.getItemLendingImage(itemLendingImage.fileName));
                                }
                                mImageSV.setImageFiles(fileList);
                            }
                        }));
        mRxDisposer.add("createView_onImageSV_deletedFile",
                mImageSV.getDeletedFileSubject()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(imageFile -> {
                            String imageFileName = imageFile.getName();
                            ItemLendingImage deletedItemLendingImage = null;
                            List<ItemLendingImage> itemLendingImageList = mItemLendingState.getItemLendingImages();
                            for (ItemLendingImage itemLendingImage : itemLendingImageList) {
                                if (imageFileName.equals(itemLendingImage.fileName)) {
                                    deletedItemLendingImage = itemLendingImage;
                                }
                            }
                            if (deletedItemLendingImage != null) {
                                itemLendingImageList.remove(deletedItemLendingImage);
                                if (isUpdate()) {
                                    mRxDisposer.add("createView_onImageSV_deletedFile_deleteItemLendingImage",
                                            mDeleteItemLendingImageCmd.execute(deletedItemLendingImage)
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe((itemImages, throwable) -> {
                                                        if (throwable != null) {
                                                            Throwable cause = throwable.getCause();
                                                            if (cause == null) cause = throwable;
                                                            mLogger.e(TAG, cause.getMessage(), cause);
                                                        } else {
                                                            mLogger.i(TAG,
                                                                    mSvProvider.getContext()
                                                                            .getString(R.string.success_delete_item_lending_image));
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
                            Future<File> itemLendingImageFile = mExecutorService.submit(() -> mItemLendingFileHelper.createItemLendingImage(Uri.fromFile(imageFile),
                                    imageFileName));
                            Future<File> itemLendingImageThumbnail = mExecutorService.submit(() -> mItemLendingFileHelper.createItemLendingImageThumbnail(Uri.fromFile(imageFile),
                                    imageFileName));
                            try {
                                itemLendingImageThumbnail.get();
                                File addImageFile = itemLendingImageFile.get();
                                ItemLendingImage itemLendingImage = new ItemLendingImage();
                                itemLendingImage.fileName = addImageFile.getName();
                                if (isUpdate()) {
                                    itemLendingImage.itemLendingId = mItemLendingState.getItemLendingId();
                                    itemLendingImage = mNewItemLendingImageCmd.execute(itemLendingImage).blockingGet();
                                    mLogger.i(TAG, mSvProvider.getContext()
                                            .getString(R.string.success_add_item_lending_image));
                                }
                                List<ItemLendingImage> itemLendingImageList = mItemLendingState.getItemLendingImages();
                                itemLendingImageList.add(itemLendingImage);
                                mImageSV.addImage(addImageFile);
                            } catch (Throwable throwable) {
                                mLogger.e(TAG, throwable.getMessage(), throwable);
                            }
                        }));
        mRxDisposer.add("createView_onBorrowerNameValid",
                mNewItemLendingCmd.getBorrowerNameValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputBorrowerName.setError(s);
                            } else {
                                inputBorrowerName.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onLendDateTimeValid",
                mNewItemLendingCmd.getLendDateTimeValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputLendDateTime.setError(s);
                            } else {
                                inputLendDateTime.setError(null);
                            }
                        }));
        return rootLayout;
    }

    private boolean isUpdate() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemLendingState != null;
        }
        return false;
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
        if (id == R.id.input_text_lend_date_time) {
            mNavigator.push(mNavExtDialogConfig.route_dateTimePickerDialog(),
                    mNavExtDialogConfig.args_dateTimePickerDialog(true, mItemLendingState.getLendDateTime()),
                    (navigator, navRoute, activity, currentView) -> lendDateTimeSelected(navRoute));
        } else if (id == R.id.input_text_due_date_time) {
            mNavigator.push(mNavExtDialogConfig.route_dateTimePickerDialog(),
                    mNavExtDialogConfig.args_dateTimePickerDialog(true, mItemLendingState.getDueDateTime()),
                    (navigator, navRoute, activity, currentView) -> dueDateTimeSelected(navRoute));
        } else if (id == R.id.button_clear_due_date_time) {
            mItemLendingState.updateDueDateTime(null);
        } else if (id == R.id.input_text_returned_date_time) {
            mNavigator.push(mNavExtDialogConfig.route_dateTimePickerDialog(),
                    mNavExtDialogConfig.args_dateTimePickerDialog(true, mItemLendingState.getReturnedDateTime()),
                    (navigator, navRoute, activity, currentView) -> returnedDateTimeSelected(navRoute));
        } else if (id == R.id.button_clear_returned_date_time) {
            mItemLendingState.updateReturnedDateTime(null);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_save) {
            if (mNewItemLendingCmd.valid(mItemLendingState)) {
                mRxDisposer.add("onMenuItemClick_save",
                        mNewItemLendingCmd.execute(mItemLendingState)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((itemLendingState, throwable) -> {
                                    if (throwable != null) {
                                        Throwable cause = throwable.getCause();
                                        if (cause == null) cause = throwable;
                                        mLogger.e(TAG, cause.getMessage(), cause);
                                    } else {
                                        int successRes = isUpdate()
                                                ? R.string.success_update_item_lending
                                                : R.string.success_add_item_lending;
                                        mLogger.i(TAG, mSvProvider.getContext()
                                                .getString(successRes));
                                        mNavigator.pop();
                                    }
                                }));
            } else {
                mLogger.i(TAG, mNewItemLendingCmd.getValidationError());
            }
        }
        return false;
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator INavigator, int requestCode, int resultCode, Intent data) {
        mImageSV.onActivityResult(currentView, activity, INavigator, requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity, INavigator INavigator, int requestCode, String[] permissions, int[] grantResults) {
        mImageSV.onRequestPermissionsResult(currentView, activity, INavigator, requestCode, permissions, grantResults);
    }

    private void lendDateTimeSelected(NavRoute navRoute) {
        Date result = mNavExtDialogConfig.result_dateTimePickerDialog(navRoute);
        if (result != null) {
            mItemLendingState.updateLendDateTime(result);
        }
    }

    private void dueDateTimeSelected(NavRoute navRoute) {
        Date result = mNavExtDialogConfig.result_dateTimePickerDialog(navRoute);
        if (result != null) {
            mItemLendingState.updateDueDateTime(result);
        }
    }

    private void returnedDateTimeSelected(NavRoute navRoute) {
        Date result = mNavExtDialogConfig.result_dateTimePickerDialog(navRoute);
        if (result != null) {
            mItemLendingState.updateReturnedDateTime(result);
        }
    }

    private Long getItemId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemId;
        }
        return null;
    }

    private ItemLendingState getItemLendingState() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemLendingState;
        }
        return null;
    }

    public static class Args implements Serializable {
        public static Args with(ItemLendingState itemLendingState) {
            Args args = new Args();
            args.itemLendingState = itemLendingState;
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
        private ItemLendingState itemLendingState;
    }
}
