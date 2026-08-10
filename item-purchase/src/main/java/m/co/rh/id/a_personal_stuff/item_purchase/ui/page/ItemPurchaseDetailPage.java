package m.co.rh.id.a_personal_stuff.item_purchase.ui.page;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.Serializable;
import java.math.BigDecimal;
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
import m.co.rh.id.a_personal_stuff.item_purchase.R;
import m.co.rh.id.a_personal_stuff.item_purchase.entity.ItemPurchaseImage;
import m.co.rh.id.a_personal_stuff.item_purchase.model.ItemPurchaseState;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.DeleteItemPurchaseImageCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.NewItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.NewItemPurchaseImageCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.QueryItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.command.UpdateItemPurchaseCmd;
import m.co.rh.id.a_personal_stuff.item_purchase.provider.component.ItemPurchaseFileHelper;
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

public class ItemPurchaseDetailPage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnActivityResult<Activity>, NavOnRequestPermissionResult<Activity>, Toolbar.OnMenuItemClickListener, View.OnClickListener {
    private static final String TAG = ItemPurchaseDetailPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient ExecutorService mExecutorService;
    private transient ItemPurchaseFileHelper mItemPurchaseFileHelper;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient RxDisposer mRxDisposer;
    private transient NewItemPurchaseCmd mNewItemPurchaseCmd;
    private transient NewItemPurchaseImageCmd mNewItemPurchaseImageCmd;
    private transient DeleteItemPurchaseImageCmd mDeleteItemPurchaseImageCmd;
    private transient QueryItemPurchaseCmd mQueryItemPurchaseCmd;

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ImageSV mImageSV;

    private ItemPurchaseState mItemPurchaseState;

    private transient TextWatcher mAmountTextWatcher;
    private transient TextWatcher mDescriptionTextWatcher;
    private transient TextWatcher mCostTextWatcher;
    private DateFormat mDateFormat;

    public ItemPurchaseDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_item_purchase_detail);
        mImageSV = new ImageSV();
        mImageSV.setSyncIndexOnReturn(true);
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mItemPurchaseFileHelper = mSvProvider.get(ItemPurchaseFileHelper.class);
        mNavExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        if (isUpdate()) {
            mNewItemPurchaseCmd = mSvProvider.get(UpdateItemPurchaseCmd.class);
        } else {
            mNewItemPurchaseCmd = mSvProvider.get(NewItemPurchaseCmd.class);
        }
        mNewItemPurchaseImageCmd = mSvProvider.get(NewItemPurchaseImageCmd.class);
        mDeleteItemPurchaseImageCmd = mSvProvider.get(DeleteItemPurchaseImageCmd.class);
        mQueryItemPurchaseCmd = mSvProvider.get(QueryItemPurchaseCmd.class);
        mAmountTextWatcher = new TextWatcher() {
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
                int amount;
                if (!s.isEmpty()) {
                    amount = Integer.parseInt(s);
                } else {
                    amount = 0;
                }
                mItemPurchaseState.setAmount(amount);
                mNewItemPurchaseCmd.valid(mItemPurchaseState);
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
                mItemPurchaseState.setDescription(s);
                mNewItemPurchaseCmd.valid(mItemPurchaseState);
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
                mItemPurchaseState.setItemPurchaseCost(cost);
            }
        };
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        if (isUpdate()) {
            mItemPurchaseState = getItemPurchaseState();
        } else {
            mItemPurchaseState = new ItemPurchaseState();
            mItemPurchaseState.setItemId(getItemId());
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_purchase_detail, container, false);
        EditText inputPurchaseDateTime = rootLayout.findViewById(R.id.input_text_purchase_date_time);
        inputPurchaseDateTime.setOnClickListener(this);
        EditText inputAmount = rootLayout.findViewById(R.id.input_text_amount);
        inputAmount.addTextChangedListener(mAmountTextWatcher);
        Button plusOneButton = rootLayout.findViewById(R.id.button_plus_1);
        plusOneButton.setOnClickListener(this);
        Button minusOneButton = rootLayout.findViewById(R.id.button_minus_1);
        minusOneButton.setOnClickListener(this);
        EditText inputCost = rootLayout.findViewById(R.id.input_text_cost);
        inputCost.addTextChangedListener(mCostTextWatcher);
        EditText inputDesc = rootLayout.findViewById(R.id.input_text_description);
        inputDesc.addTextChangedListener(mDescriptionTextWatcher);
        ViewGroup imageContainer = rootLayout.findViewById(R.id.container_image_component);
        imageContainer.addView(mImageSV.buildView(activity, imageContainer));
        if (isUpdate()) {
            mAppBarSV.setTitle(activity.getString(R.string.title_update_item_purchase));
        } else {
            mAppBarSV.setTitle(activity.getString(R.string.title_add_item_purchase));
        }
        mAppBarSV.setMenuItemClick(this);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSV.buildView(activity, appBarContainer));
        mRxDisposer.add("createView_onItemPurchaseChanged",
                mItemPurchaseState.getItemPurchaseFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemPurchase -> {
                            inputAmount.setText(String.valueOf(itemPurchase.amount));
                            inputDesc.setText(itemPurchase.description);
                            BigDecimal cost = itemPurchase.cost;
                            if (cost != null && cost.signum() != 0) {
                                inputCost.setText(cost.toPlainString());
                            }
                            Date purchaseDateTime = itemPurchase.purchaseDateTime;
                            if (purchaseDateTime != null) {
                                inputPurchaseDateTime.setText(mDateFormat.format(purchaseDateTime));
                            }
                        }));
        mRxDisposer.add("createView_onItemPurchaseImagesChanged",
                mItemPurchaseState.getItemPurchaseImagesFlow()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(itemPurchaseImages -> {
                            if (!itemPurchaseImages.isEmpty()) {
                                List<File> fileList = new ArrayList<>();
                                for (ItemPurchaseImage itemPurchaseImage : itemPurchaseImages) {
                                    fileList.add(mItemPurchaseFileHelper.getItemPurchaseImage(itemPurchaseImage.fileName));
                                }
                                mImageSV.setImageFiles(fileList);
                            }
                        }));
        mRxDisposer.add("createView_onImageSV_deletedFile",
                mImageSV.getDeletedFileSubject()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(imageFile -> {
                            String imageFileName = imageFile.getName();
                            ItemPurchaseImage deletedItemPurchaseImage = null;
                            List<ItemPurchaseImage> itemPurchaseImageList = mItemPurchaseState.getItemPurchaseImages();
                            for (ItemPurchaseImage itemPurchaseImage : itemPurchaseImageList) {
                                if (imageFileName.equals(itemPurchaseImage.fileName)) {
                                    deletedItemPurchaseImage = itemPurchaseImage;
                                }
                            }
                            if (deletedItemPurchaseImage != null) {
                                itemPurchaseImageList.remove(deletedItemPurchaseImage);
                                if (isUpdate()) {
                                    mRxDisposer.add("createView_onImageSV_deletedFile_deleteItemPurchaseImage",
                                            mDeleteItemPurchaseImageCmd.execute(deletedItemPurchaseImage)
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe((itemImages, throwable) -> {
                                                        if (throwable != null) {
                                                            Throwable cause = throwable.getCause();
                                                            if (cause == null) cause = throwable;
                                                            mLogger.e(TAG, cause.getMessage(), cause);
                                                        } else {
                                                            mLogger.i(TAG,
                                                                    mSvProvider.getContext()
                                                                            .getString(R.string.success_delete_item_purchase_image));
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
                            Future<File> itemPurchaseImageFile = mExecutorService.submit(() -> mItemPurchaseFileHelper.createItemPurchaseImage(Uri.fromFile(imageFile),
                                    imageFileName));
                            Future<File> itemPurchaseImageThumbnail = mExecutorService.submit(() -> mItemPurchaseFileHelper.createItemPurchaseImageThumbnail(Uri.fromFile(imageFile),
                                    imageFileName));
                            try {
                                itemPurchaseImageThumbnail.get();
                                File addImageFile = itemPurchaseImageFile.get();
                                ItemPurchaseImage itemPurchaseImage = new ItemPurchaseImage();
                                itemPurchaseImage.fileName = addImageFile.getName();
                                if (isUpdate()) {
                                    itemPurchaseImage.itemPurchaseId = mItemPurchaseState.getItemPurchaseId();
                                    itemPurchaseImage = mNewItemPurchaseImageCmd.execute(itemPurchaseImage).blockingGet();
                                    mLogger.i(TAG, mSvProvider.getContext()
                                            .getString(R.string.success_add_item_purchase_image));
                                }
                                List<ItemPurchaseImage> itemPurchaseImageList = mItemPurchaseState.getItemPurchaseImages();
                                itemPurchaseImageList.add(itemPurchaseImage);
                                mImageSV.addImage(addImageFile);
                            } catch (Throwable throwable) {
                                mLogger.e(TAG, throwable.getMessage(), throwable);
                            }
                        }));
        mRxDisposer.add("createView_onAmountValid",
                mNewItemPurchaseCmd.getAmountValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputAmount.setError(s);
                            } else {
                                inputAmount.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onDescriptionValid",
                mNewItemPurchaseCmd.getDescriptionValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputDesc.setError(s);
                            } else {
                                inputDesc.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onPurchaseDateTimeValid",
                mNewItemPurchaseCmd.getPurchaseDateTimeValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputPurchaseDateTime.setError(s);
                            } else {
                                inputPurchaseDateTime.setError(null);
                            }
                        }));
        return rootLayout;
    }

    private boolean isUpdate() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemPurchaseState != null;
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
        if (id == R.id.input_text_purchase_date_time) {
            mNavigator.push(mNavExtDialogConfig.route_dateTimePickerDialog(),
                    mNavExtDialogConfig.args_dateTimePickerDialog(true, mItemPurchaseState.getPurchaseDateTime()),
                    (navigator, navRoute, activity, currentView) -> purchaseDateTimeSelected(navRoute));
        } else if (id == R.id.button_plus_1) {
            ViewParent viewParent = view.getParent();
            if (viewParent instanceof ViewGroup) {
                if (((ViewGroup) viewParent).getId() == R.id.container_amount) {
                    mItemPurchaseState.increaseAmount(1);
                }
            }
        } else if (id == R.id.button_minus_1) {
            ViewParent viewParent = view.getParent();
            if (viewParent instanceof ViewGroup) {
                if (((ViewGroup) viewParent).getId() == R.id.container_amount) {
                    int currentAmount = mItemPurchaseState.getItemPurchaseAmount();
                    if (currentAmount > 1) {
                        mItemPurchaseState.decreaseAmount(1);
                    }
                }
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_save) {
            if (mNewItemPurchaseCmd.valid(mItemPurchaseState)) {
                mRxDisposer.add("onMenuItemClick_save",
                        mNewItemPurchaseCmd.execute(mItemPurchaseState)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((itemPurchaseState, throwable) -> {
                                    if (throwable != null) {
                                        Throwable cause = throwable.getCause();
                                        if (cause == null) cause = throwable;
                                        mLogger.e(TAG, cause.getMessage(), cause);
                                    } else {
                                        int successRes = isUpdate()
                                                ? R.string.success_update_item_purchase
                                                : R.string.success_add_item_purchase;
                                        mLogger.i(TAG, mSvProvider.getContext()
                                                .getString(successRes));
                                        mNavigator.pop();
                                    }
                                }));
            } else {
                mLogger.i(TAG, mNewItemPurchaseCmd.getValidationError());
            }
        }
        return false;
    }

    private void purchaseDateTimeSelected(NavRoute navRoute) {
        Date result = mNavExtDialogConfig.result_dateTimePickerDialog(navRoute);
        if (result != null) {
            mItemPurchaseState.updatePurchaseDateTime(result);
        }
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator INavigator, int requestCode, int resultCode, Intent data) {
        mImageSV.onActivityResult(currentView, activity, INavigator, requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity, INavigator INavigator, int requestCode, String[] permissions, int[] grantResults) {
        mImageSV.onRequestPermissionsResult(currentView, activity, INavigator, requestCode, permissions, grantResults);
    }

    private ItemPurchaseState getItemPurchaseState() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemPurchaseState;
        }
        return null;
    }

    private Long getItemId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemId;
        }
        return null;
    }

    public static class Args implements Serializable {
        public static Args with(ItemPurchaseState itemPurchaseState) {
            Args args = new Args();
            args.itemPurchaseState = itemPurchaseState;
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
        private ItemPurchaseState itemPurchaseState;
    }
}
