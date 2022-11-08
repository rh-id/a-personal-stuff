package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.widget.Toolbar;

import com.google.android.material.chip.Chip;

import java.io.File;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.command.DeleteItemImageCmd;
import m.co.rh.id.a_personal_stuff.app.provider.command.DeleteItemTagCmd;
import m.co.rh.id.a_personal_stuff.app.provider.command.NewItemCmd;
import m.co.rh.id.a_personal_stuff.app.provider.command.NewItemImageCmd;
import m.co.rh.id.a_personal_stuff.app.provider.command.NewItemTagCmd;
import m.co.rh.id.a_personal_stuff.app.provider.command.QueryItemCmd;
import m.co.rh.id.a_personal_stuff.app.provider.command.UpdateItemCmd;
import m.co.rh.id.a_personal_stuff.app.ui.component.adapter.SuggestionAdapter;
import m.co.rh.id.a_personal_stuff.barcode.ui.NavBarcodeConfig;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.ImageSV;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.anavigator.extension.dialog.ui.NavExtDialogConfig;
import m.co.rh.id.aprovider.Provider;

public class ItemDetailPage extends StatefulView<Activity> implements RequireNavRoute, RequireComponent<Provider>, NavOnActivityResult<Activity>, NavOnRequestPermissionResult<Activity>, View.OnClickListener, Toolbar.OnMenuItemClickListener {

    private static final String TAG = ItemDetailPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    private transient NavRoute mNavRoute;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ImageSV mImageSV;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient ExecutorService mExecutorService;
    private transient NavExtDialogConfig mNavExtDialogConfig;
    private transient NavBarcodeConfig mNavBarcodeConfig;
    private transient ItemFileHelper mItemFileHelper;
    private transient RxDisposer mRxDisposer;
    private transient NewItemCmd mNewItemCmd;
    private transient DeleteItemImageCmd mDeleteItemImageCmd;
    private transient NewItemImageCmd mNewItemImageCmd;
    private transient DeleteItemTagCmd mDeleteItemTagCmd;
    private transient NewItemTagCmd mNewItemTagCmd;
    private transient QueryItemCmd mQueryItemCmd;
    private transient CompositeDisposable mCompositeDisposable;

    private ItemState mItemState;

    private DateFormat mDateFormat;
    private transient TextWatcher mNameTextWatcher;
    private transient TextWatcher mAmountTextWatcher;
    private transient TextWatcher mPriceTextWatcher;
    private transient TextWatcher mDescriptionTextWatcher;
    private transient TextWatcher mBarcodeTextWatcher;
    private transient Function<String, Collection<String>> mBarcodeSuggestionQuery;
    private transient Function<String, Collection<String>> mTagSuggestionQuery;

    public ItemDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_item_detail);
        mImageSV = new ImageSV();
    }

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mNavExtDialogConfig = mSvProvider.get(NavExtDialogConfig.class);
        mNavBarcodeConfig = mSvProvider.get(NavBarcodeConfig.class);
        mItemFileHelper = mSvProvider.get(ItemFileHelper.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        if (isUpdate()) {
            mNewItemCmd = mSvProvider.get(UpdateItemCmd.class);
            mDeleteItemImageCmd = mSvProvider.get(DeleteItemImageCmd.class);
            mNewItemImageCmd = mSvProvider.get(NewItemImageCmd.class);
        } else {
            mNewItemCmd = mSvProvider.get(NewItemCmd.class);
        }
        mDeleteItemTagCmd = mSvProvider.get(DeleteItemTagCmd.class);
        mNewItemTagCmd = mSvProvider.get(NewItemTagCmd.class);
        mQueryItemCmd = mSvProvider.get(QueryItemCmd.class);
        mCompositeDisposable = new CompositeDisposable();
        mNameTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String s = editable.toString();
                mItemState.setItemName(s);
                mNewItemCmd.valid(mItemState);
            }
        };
        mAmountTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
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
                mItemState.setItemAmount(amount);
                mNewItemCmd.valid(mItemState);
            }
        };
        mPriceTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String s = editable.toString();
                Locale locale = mNavigator.getActivity().getResources().getConfiguration().locale;
                BigDecimal price = null;
                try {
                    Number number = NumberFormat.getInstance(locale).parse(s);
                    if (number != null) {
                        price = new BigDecimal(number.toString());
                    }
                } catch (Exception e) {
                    mLogger.d(TAG, "Failed to set price, string val: " + s + "||locale: " + locale, e);
                }
                mItemState.setItemPrice(price);
            }
        };
        mDescriptionTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String s = editable.toString();
                mItemState.setItemDescription(s);
            }
        };
        mBarcodeTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String s = editable.toString();
                mItemState.setItemBarcode(s);
            }
        };
        mBarcodeSuggestionQuery = s -> mQueryItemCmd.searchItemBarcode(s).blockingGet();
        mTagSuggestionQuery = s -> {
            Set<String> stringSet = mQueryItemCmd.searchItemTag(s).blockingGet();
            Collection<ItemTag> tagSet = mItemState.getItemTags();
            if (!tagSet.isEmpty()) {
                for (ItemTag itemTag : tagSet) {
                    stringSet.remove(itemTag.tag);
                }
            }
            return stringSet;
        };
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
        if (isUpdate()) {
            mItemState = getItemStateArgs();
        } else {
            mItemState = new ItemState();
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_detail, container, false);
        Locale locale = activity.getResources().getConfiguration().locale;
        EditText inputName = rootLayout.findViewById(R.id.input_text_name);
        inputName.addTextChangedListener(mNameTextWatcher);
        EditText inputAmount = rootLayout.findViewById(R.id.input_text_amount);
        inputAmount.addTextChangedListener(mAmountTextWatcher);
        EditText inputPrice = rootLayout.findViewById(R.id.input_text_price);
        inputPrice.addTextChangedListener(mPriceTextWatcher);
        EditText inputDescription = rootLayout.findViewById(R.id.input_text_description);
        inputDescription.addTextChangedListener(mDescriptionTextWatcher);
        AutoCompleteTextView inputBarcode = rootLayout.findViewById(R.id.input_text_barcode);
        inputBarcode.addTextChangedListener(mBarcodeTextWatcher);
        inputBarcode.setThreshold(1);
        inputBarcode.setAdapter(new SuggestionAdapter
                (activity, android.R.layout.select_dialog_item, mBarcodeSuggestionQuery));
        EditText inputExpiredDateTime = rootLayout.findViewById(R.id.input_text_expired_date_time);
        inputExpiredDateTime.setOnClickListener(this);
        ViewGroup tagDisplayContainer = rootLayout.findViewById(R.id.container_tag_display);
        AutoCompleteTextView tagText = rootLayout.findViewById(R.id.input_text_tag);
        tagText.setThreshold(1);
        tagText.setAdapter(new SuggestionAdapter
                (activity, android.R.layout.select_dialog_item, mTagSuggestionQuery));
        View containerAmount = rootLayout.findViewById(R.id.container_amount);
        Button amountPlus1 = containerAmount.findViewById(R.id.button_plus_1);
        amountPlus1.setOnClickListener(this);
        Button amountMinus1 = containerAmount.findViewById(R.id.button_minus_1);
        amountMinus1.setOnClickListener(this);
        Button scanBarcode = rootLayout.findViewById(R.id.button_scan_barcode);
        scanBarcode.setOnClickListener(this);
        Button clearExpiredDateTimeButton = rootLayout.findViewById(R.id.button_clear_expired_date_time);
        clearExpiredDateTimeButton.setOnClickListener(this);
        Button addTagButton = rootLayout.findViewById(R.id.button_add_tag);
        addTagButton.setOnClickListener(this);
        ViewGroup containerImageComponent = rootLayout.findViewById(R.id.container_image_component);
        containerImageComponent.addView(mImageSV.buildView(activity, containerImageComponent));
        String title;
        if (isUpdate()) {
            title = activity.getString(R.string.title_update_item);
        } else {
            title = activity.getString(R.string.title_add_item);
        }
        mAppBarSV.setTitle(title);
        mAppBarSV.setMenuItemClick(this);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, containerAppBar));
        mRxDisposer.add("createView_onItemChanged",
                mItemState.getItemFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(item -> {
                            inputName.setText(item.name);
                            inputAmount.setText(String.valueOf(item.amount));
                            if (item.price != null) {
                                inputPrice.setText(NumberFormat.getInstance(locale)
                                        .format(item.price));
                            }
                            inputDescription.setText(item.description);
                            inputBarcode.setText(item.barcode);
                            if (item.expiredDateTime != null) {
                                inputExpiredDateTime.setText(mDateFormat.format(item.expiredDateTime));
                            } else {
                                inputExpiredDateTime.setText(null);
                            }
                        }));
        mRxDisposer.add("createView_onItemTagsChanged",
                mItemState.getItemTagsFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemTags -> {
                            tagDisplayContainer.removeAllViews();
                            if (!itemTags.isEmpty()) {
                                boolean isUpdate = isUpdate();
                                for (ItemTag itemTag : itemTags) {
                                    Chip chip = new Chip(activity);
                                    chip.setText(itemTag.tag);
                                    chip.setOnCloseIconClickListener(view -> {
                                        tagDisplayContainer.removeView(chip);
                                        chip.setOnCloseIconClickListener(null);
                                        TreeSet<ItemTag> tagSet = mItemState.getItemTags();
                                        tagSet.remove(itemTag);
                                        if (isUpdate && itemTag.id != null) {
                                            Context context = activity.getApplicationContext();
                                            mCompositeDisposable.add(mDeleteItemTagCmd.execute(itemTag)
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe((deletedNoteTag, throwable) -> {
                                                        String successMessage = context.getString(R.string.success_deleting_tag);
                                                        if (throwable != null) {
                                                            Throwable cause = throwable.getCause();
                                                            if (cause == null) cause = throwable;
                                                            mLogger
                                                                    .e(TAG, cause.getMessage(), cause);
                                                        } else {
                                                            mLogger
                                                                    .i(TAG, successMessage);
                                                        }
                                                    }));
                                        }
                                    });
                                    chip.setCloseIconVisible(true);
                                    tagDisplayContainer.addView(chip);
                                }
                                tagDisplayContainer.setVisibility(View.VISIBLE);
                            } else {
                                tagDisplayContainer.setVisibility(View.GONE);
                            }
                        }));
        mRxDisposer.add("createView_onItemImageChanged",
                mItemState.getItemImagesFlow().observeOn(Schedulers.from(mExecutorService))
                        .subscribe(itemImages -> {
                            if (!itemImages.isEmpty()) {
                                List<File> imageFiles = new ArrayList<>();
                                for (ItemImage itemImage : itemImages) {
                                    File file = mItemFileHelper.getItemImage(itemImage.fileName);
                                    imageFiles.add(file);
                                }
                                mImageSV.setImageFiles(imageFiles);
                            } else {
                                mImageSV.setImageFiles(null);
                            }
                        }));
        mRxDisposer.add("createView_onImageSv_addFile",
                mImageSV.getAddFileSubject()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(imageFile -> {
                            String imageFileName = imageFile.getName();
                            Future<File> itemImageFile = mExecutorService.submit(() -> mItemFileHelper.createItemImage(Uri.fromFile(imageFile),
                                    imageFileName));
                            Future<File> itemImageThumbnail = mExecutorService.submit(() -> mItemFileHelper.createItemImageThumbnail(Uri.fromFile(imageFile),
                                    imageFileName));
                            try {
                                itemImageThumbnail.get();
                                File addImageFile = itemImageFile.get();
                                ItemImage itemImage = new ItemImage();
                                itemImage.fileName = addImageFile.getName();
                                if (isUpdate()) {
                                    itemImage.itemId = mItemState.getItemId();
                                    itemImage = mNewItemImageCmd.execute(itemImage).blockingGet();
                                    mLogger.i(TAG, mSvProvider.getContext()
                                            .getString(R.string.success_add_item_image));
                                }
                                List<ItemImage> itemImageList = mItemState.getItemImages();
                                itemImageList.add(itemImage);
                                mImageSV.addImage(addImageFile);
                            } catch (Throwable throwable) {
                                mLogger.e(TAG, throwable.getMessage(), throwable);
                            }
                        }));
        mRxDisposer.add("createView_onImageSv_deletedFile",
                mImageSV.getDeletedFileSubject()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(imageFile -> {
                            String imageFileName = imageFile.getName();
                            ItemImage deletedItemImage = null;
                            List<ItemImage> itemImageList = mItemState.getItemImages();
                            for (ItemImage itemImage : itemImageList) {
                                if (imageFileName.equals(itemImage.fileName)) {
                                    deletedItemImage = itemImage;
                                }
                            }
                            if (deletedItemImage != null) {
                                itemImageList.remove(deletedItemImage);
                                if (isUpdate()) {
                                    mRxDisposer.add("createView_onImageSv_deletedFile_deleteItemImage",
                                            mDeleteItemImageCmd.execute(Collections.singletonList(deletedItemImage))
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe((itemImages, throwable) -> {
                                                        if (throwable != null) {
                                                            Throwable cause = throwable.getCause();
                                                            if (cause == null) cause = throwable;
                                                            mLogger.e(TAG, cause.getMessage(), cause);
                                                        } else {
                                                            mLogger.i(TAG,
                                                                    mSvProvider.getContext()
                                                                            .getString(R.string.success_delete_item_image));
                                                        }
                                                    })
                                    );
                                }
                            }
                        }));
        mRxDisposer.add("createView_onItemNameValid",
                mNewItemCmd.getNameValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputName.setError(s);
                            } else {
                                inputName.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onItemAmountValid",
                mNewItemCmd.getAmountValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputAmount.setError(s);
                            } else {
                                inputAmount.setError(null);
                            }
                        }));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mImageSV.dispose(activity);
        mImageSV = null;
        if (mCompositeDisposable != null) {
            mCompositeDisposable.dispose();
            mCompositeDisposable = null;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_plus_1) {
            ViewParent viewParent = view.getParent();
            if (viewParent instanceof ViewGroup) {
                if (((ViewGroup) viewParent).getId() == R.id.container_amount) {
                    mItemState.increaseItemAmount(1);
                }
            }
        } else if (id == R.id.button_minus_1) {
            ViewParent viewParent = view.getParent();
            if (viewParent instanceof ViewGroup) {
                if (((ViewGroup) viewParent).getId() == R.id.container_amount) {
                    mItemState.decreaseItemAmount(1);
                }
            }
        } else if (id == R.id.button_scan_barcode) {
            mNavigator.push(mNavBarcodeConfig.route_scanBarcodePage(),
                    (navigator, navRoute, activity, currentView) -> updateBarcodeResult(navRoute));
        } else if (id == R.id.button_clear_expired_date_time) {
            mItemState.updateItemExpiredDateTime(null);
        } else if (id == R.id.button_add_tag) {
            ViewGroup viewGroup = (ViewGroup) view.getParent();
            EditText tagText = viewGroup.findViewById(R.id.input_text_tag);
            String tag = tagText.getText().toString();
            tagText.setText(null);
            if (!tag.isEmpty()) {
                ItemTag itemTag = new ItemTag();
                itemTag.tag = tag;
                if (isUpdate()) {
                    itemTag.itemId = mItemState.getItemId();
                    mCompositeDisposable.add(
                            mNewItemTagCmd.execute(itemTag).subscribe((itemTag1, throwable) -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause();
                                    if (cause == null) cause = throwable;
                                    mLogger.e(TAG, cause.getMessage(), cause);
                                } else {
                                    mItemState.addItemTag(itemTag);
                                    mLogger.i(TAG, mSvProvider.getContext()
                                            .getString(R.string.success_add_item_tag));
                                }
                            })
                    );
                } else {
                    mItemState.addItemTag(itemTag);
                }

            }
        } else if (id == R.id.input_text_expired_date_time) {
            mNavigator.push(mNavExtDialogConfig.route_dateTimePickerDialog(),
                    mNavExtDialogConfig.args_dateTimePickerDialog(true, mItemState.getItemExpiredDateTime())
                    , (navigator, navRoute, activity, currentView) -> updateExpiredDateTime(navRoute));
        }
    }

    private void updateBarcodeResult(NavRoute navRoute) {
        mItemState.updateBarcode(mNavBarcodeConfig.result_scanBarcodePage_barcode(navRoute));
    }

    private void updateExpiredDateTime(NavRoute navRoute) {
        Date selectedDate = mNavExtDialogConfig.result_dateTimePickerDialog(navRoute);
        if (selectedDate != null) {
            mItemState.updateItemExpiredDateTime(selectedDate);
        }
    }

    private boolean isUpdate() {
        return getItemStateArgs() != null;
    }

    private ItemState getItemStateArgs() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemState;
        }
        return null;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            if (mNewItemCmd.valid(mItemState)) {
                mRxDisposer.add("onMenuItemClick_save",
                        mNewItemCmd.execute(mItemState).observeOn(AndroidSchedulers.mainThread())
                                .subscribe((itemState, throwable) -> {
                                    if (throwable != null) {
                                        Throwable cause = throwable.getCause();
                                        if (cause == null) {
                                            cause = throwable;
                                        }
                                        mLogger.e(TAG, cause.getMessage(), cause);
                                    } else {
                                        Context context = mSvProvider.getContext();
                                        String msg;
                                        if (isUpdate()) {
                                            msg = context.getString(R.string.success_update_item_, itemState.getItemName());
                                        } else {
                                            msg = context.getString(R.string.success_add_item_, itemState.getItemName());
                                        }
                                        mLogger.i(TAG, msg);
                                        mNavigator.pop(Result.with(itemState));
                                    }
                                }));
            } else {
                String error = mNewItemCmd.getValidationError();
                mLogger.i(TAG, error);
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

    static class Result implements Serializable {
        static Result with(ItemState itemState) {
            Result result = new Result();
            result.itemState = itemState;
            return result;
        }

        private ItemState itemState;
    }

    public static class Args implements Serializable {
        public static Args forUpdate(ItemState itemState) {
            Args args = new Args();
            args.itemState = itemState;
            return args;
        }

        static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                Serializable arg = navRoute.getRouteArgs();
                if (arg instanceof Args) {
                    return (Args) arg;
                }
            }
            return null;
        }

        private ItemState itemState;
    }
}
