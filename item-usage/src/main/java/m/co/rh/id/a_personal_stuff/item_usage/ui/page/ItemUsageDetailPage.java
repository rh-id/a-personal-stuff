package m.co.rh.id.a_personal_stuff.item_usage.ui.page;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.ImageSV;
import m.co.rh.id.a_personal_stuff.item_usage.R;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsageImage;
import m.co.rh.id.a_personal_stuff.item_usage.model.ItemUsageState;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.DeleteItemUsageImageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.NewItemUsageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.NewItemUsageImageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.UpdateItemUsageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.provider.component.ItemUsageFileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemUsageDetailPage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnActivityResult<Activity>, NavOnRequestPermissionResult<Activity>, Toolbar.OnMenuItemClickListener, View.OnClickListener {
    private static final String TAG = ItemUsageDetailPage.class.getName();

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient ExecutorService mExecutorService;
    private transient ItemUsageFileHelper mItemUsageFileHelper;
    private transient RxDisposer mRxDisposer;
    private transient NewItemUsageCmd mNewItemUsageCmd;
    private transient NewItemUsageImageCmd mNewItemUsageImageCmd;
    private transient DeleteItemUsageImageCmd mDeleteItemUsageImageCmd;

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ImageSV mImageSV;

    private ItemUsageState mItemUsageState;

    private transient TextWatcher mAmountTextWatcher;
    private transient TextWatcher mDescriptionTextWatcher;

    public ItemUsageDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_item_usage_detail);
        mImageSV = new ImageSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mItemUsageFileHelper = mSvProvider.get(ItemUsageFileHelper.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        if (isUpdate()) {
            mNewItemUsageCmd = mSvProvider.get(UpdateItemUsageCmd.class);
        } else {
            mNewItemUsageCmd = mSvProvider.get(NewItemUsageCmd.class);
        }
        mNewItemUsageImageCmd = mSvProvider.get(NewItemUsageImageCmd.class);
        mDeleteItemUsageImageCmd = mSvProvider.get(DeleteItemUsageImageCmd.class);
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
                mItemUsageState.setAmount(amount);
                mNewItemUsageCmd.valid(mItemUsageState);
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
                mItemUsageState.setDescription(s);
                mNewItemUsageCmd.valid(mItemUsageState);
            }
        };
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        if (isUpdate()) {
            mItemUsageState = getItemUsageState();
        } else {
            mItemUsageState = new ItemUsageState();
            mItemUsageState.setItemId(getItemId());
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_item_usage_detail, container, false);
        EditText inputAmount = rootLayout.findViewById(R.id.input_text_amount);
        inputAmount.addTextChangedListener(mAmountTextWatcher);
        Button plusOneButton = rootLayout.findViewById(R.id.button_plus_1);
        plusOneButton.setOnClickListener(this);
        Button minusOneButton = rootLayout.findViewById(R.id.button_minus_1);
        minusOneButton.setOnClickListener(this);
        EditText inputDesc = rootLayout.findViewById(R.id.input_text_description);
        inputDesc.addTextChangedListener(mDescriptionTextWatcher);
        ViewGroup imageContainer = rootLayout.findViewById(R.id.container_image_component);
        imageContainer.addView(mImageSV.buildView(activity, imageContainer));
        if (isUpdate()) {
            mAppBarSV.setTitle(activity.getString(R.string.title_update_item_usage));
        } else {
            mAppBarSV.setTitle(activity.getString(R.string.title_add_item_usage));
        }
        mAppBarSV.setMenuItemClick(this);
        ViewGroup appBarContainer = rootLayout.findViewById(R.id.container_app_bar);
        appBarContainer.addView(mAppBarSV.buildView(activity, appBarContainer));
        mRxDisposer.add("createView_onItemUsageChanged",
                mItemUsageState.getItemUsageFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemUsage -> {
                            inputAmount.setText(String.valueOf(itemUsage.amount));
                            inputDesc.setText(itemUsage.description);
                        }));
        mRxDisposer.add("createView_onItemUsageImagesChanged",
                mItemUsageState.getItemUsageImagesFlow()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(itemUsageImages -> {
                            if (!itemUsageImages.isEmpty()) {
                                List<File> fileList = new ArrayList<>();
                                for (ItemUsageImage itemUsageImage : itemUsageImages) {
                                    fileList.add(mItemUsageFileHelper.getItemUsageImage(itemUsageImage.fileName));
                                }
                                mImageSV.setImageFiles(fileList);
                            }
                        }));
        mRxDisposer.add("createView_onImageSV_deletedFile",
                mImageSV.getDeletedFileSubject()
                        .observeOn(Schedulers.from(mExecutorService))
                        .subscribe(imageFile -> {
                            String imageFileName = imageFile.getName();
                            ItemUsageImage deletedItemImage = null;
                            List<ItemUsageImage> itemUsageImageList = mItemUsageState.getItemUsageImages();
                            for (ItemUsageImage itemUsageImage : itemUsageImageList) {
                                if (imageFileName.equals(itemUsageImage.fileName)) {
                                    deletedItemImage = itemUsageImage;
                                }
                            }
                            if (deletedItemImage != null) {
                                itemUsageImageList.remove(deletedItemImage);
                                if (isUpdate()) {
                                    mRxDisposer.add("createView_onImageSV_deletedFile_deleteItemUsageImage",
                                            mDeleteItemUsageImageCmd.execute(deletedItemImage)
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe((itemImages, throwable) -> {
                                                        if (throwable != null) {
                                                            Throwable cause = throwable.getCause();
                                                            if (cause == null) cause = throwable;
                                                            mLogger.e(TAG, cause.getMessage(), cause);
                                                        } else {
                                                            mLogger.i(TAG,
                                                                    mSvProvider.getContext()
                                                                            .getString(R.string.success_delete_item_usage_image));
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
                            Future<File> itemUsageImageFile = mExecutorService.submit(() -> mItemUsageFileHelper.createItemUsageImage(Uri.fromFile(imageFile),
                                    imageFileName));
                            Future<File> itemUsageImageThumbnail = mExecutorService.submit(() -> mItemUsageFileHelper.createItemUsageImageThumbnail(Uri.fromFile(imageFile),
                                    imageFileName));
                            try {
                                itemUsageImageThumbnail.get();
                                File addImageFile = itemUsageImageFile.get();
                                ItemUsageImage itemUsageImage = new ItemUsageImage();
                                itemUsageImage.fileName = addImageFile.getName();
                                if (isUpdate()) {
                                    itemUsageImage.itemUsageId = mItemUsageState.getItemUsageId();
                                    itemUsageImage = mNewItemUsageImageCmd.execute(itemUsageImage).blockingGet();
                                    mLogger.i(TAG, mSvProvider.getContext()
                                            .getString(R.string.success_add_item_usage_image));
                                }
                                List<ItemUsageImage> itemUsageImageList = mItemUsageState.getItemUsageImages();
                                itemUsageImageList.add(itemUsageImage);
                                mImageSV.addImage(addImageFile);
                            } catch (Throwable throwable) {
                                mLogger.e(TAG, throwable.getMessage(), throwable);
                            }
                        }));
        mRxDisposer.add("createView_onAmountValid",
                mNewItemUsageCmd.getAmountValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputAmount.setError(s);
                            } else {
                                inputAmount.setError(null);
                            }
                        }));
        mRxDisposer.add("createView_onDescriptionValid",
                mNewItemUsageCmd.getDescriptionValidFlow().observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                inputDesc.setError(s);
                            } else {
                                inputDesc.setError(null);
                            }
                        }));
        return rootLayout;
    }

    private boolean isUpdate() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemUsageState != null;
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
        if (id == R.id.button_plus_1) {
            ViewParent viewParent = view.getParent();
            if (viewParent instanceof ViewGroup) {
                if (((ViewGroup) viewParent).getId() == R.id.container_amount) {
                    mItemUsageState.increaseAmount(1);
                }
            }
        } else if (id == R.id.button_minus_1) {
            ViewParent viewParent = view.getParent();
            if (viewParent instanceof ViewGroup) {
                if (((ViewGroup) viewParent).getId() == R.id.container_amount) {
                    mItemUsageState.decreaseAmount(1);
                }
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_save) {
            if (mNewItemUsageCmd.valid(mItemUsageState)) {
                mRxDisposer.add("onMenuItemClick_save",
                        mNewItemUsageCmd.execute(mItemUsageState)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe((itemUsageState, throwable) -> {
                                    if (throwable != null) {
                                        Throwable cause = throwable.getCause();
                                        if (cause == null) cause = throwable;
                                        mLogger.e(TAG, cause.getMessage(), cause);
                                    } else {
                                        mLogger.i(TAG, mSvProvider.getContext()
                                                .getString(R.string.success_add_item_usage));
                                        mNavigator.pop();
                                    }
                                }));
            } else {
                mLogger.i(TAG, mNewItemUsageCmd.getValidationError());
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

    private Long getItemId() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemId;
        }
        return null;
    }

    private ItemUsageState getItemUsageState() {
        Args args = Args.of(mNavRoute);
        if (args != null) {
            return args.itemUsageState;
        }
        return null;
    }

    public static class Args implements Serializable {
        public static Args with(ItemUsageState itemUsageState) {
            Args args = new Args();
            args.itemUsageState = itemUsageState;
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
        private ItemUsageState itemUsageState;
    }
}
