package m.co.rh.id.a_personal_stuff.app.ui.component.item;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;

import java.io.File;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import co.rh.id.lib.rx3_utils.subject.SerialOptionalBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.provider.component.ItemFileHelper;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.ImageViewPage;
import m.co.rh.id.a_personal_stuff.item_maintenance.ui.page.ItemMaintenancesPage;
import m.co.rh.id.a_personal_stuff.item_reminder.ui.page.ItemRemindersPage;
import m.co.rh.id.a_personal_stuff.item_usage.entity.ItemUsage;
import m.co.rh.id.a_personal_stuff.item_usage.provider.command.QueryItemUsageCmd;
import m.co.rh.id.a_personal_stuff.item_usage.ui.page.ItemUsagesPage;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener, PopupMenu.OnMenuItemClickListener {

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ItemFileHelper mItemFileHelper;
    private transient RxDisposer mRxDisposer;
    private transient QueryItemUsageCmd mQueryItemUsageCmd;

    private final SerialBehaviorSubject<ItemState> mItemState;
    private final SerialOptionalBehaviorSubject<Integer> mUsageCount;
    private final DateFormat mDateFormat;
    private transient BehaviorSubject<Optional<ItemImage>> mItemImageDisplay;
    private transient int mDefaultExpiredDateColor;
    private transient BehaviorSubject<Integer> mExpiredDateColor;

    private transient OnItemEditClicked mOnItemEditClicked;
    private transient OnItemDeleteClicked mOnItemDeleteClicked;

    public ItemItemSV() {
        mItemState = new SerialBehaviorSubject<>();
        mUsageCount = new SerialOptionalBehaviorSubject<>();
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mItemFileHelper = mSvProvider.get(ItemFileHelper.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mQueryItemUsageCmd = mSvProvider.get(QueryItemUsageCmd.class);
        mItemImageDisplay = BehaviorSubject.create();
        mDefaultExpiredDateColor = ContextCompat.getColor(provider.getContext(), m.co.rh.id.a_personal_stuff.base.R.color.light_green_600);
        mExpiredDateColor = BehaviorSubject.createDefault(mDefaultExpiredDateColor);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_item, container, false);
        ImageView imageViewThumbnail = rootLayout.findViewById(R.id.imageView_thumbnail);
        imageViewThumbnail.setOnClickListener(this);
        TextView expireDateTimeText = rootLayout.findViewById(R.id.text_expired_date_time);
        TextView nameText = rootLayout.findViewById(R.id.text_name);
        TextView amountText = rootLayout.findViewById(R.id.text_amount);
        TextView priceText = rootLayout.findViewById(R.id.text_price);
        TextView barcodeText = rootLayout.findViewById(R.id.text_barcode);
        Button editButton = rootLayout.findViewById(R.id.button_edit);
        editButton.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);
        Button moreActionButton = rootLayout.findViewById(R.id.button_more_action);
        moreActionButton.setOnClickListener(this);
        Button usageCountButton = rootLayout.findViewById(R.id.button_usage_count);
        usageCountButton.setOnClickListener(this);
        ViewGroup tagDisplayContainer = rootLayout.findViewById(R.id.container_tag_display);
        mRxDisposer.add("createView_onExpiredDateColorChanged",
                mExpiredDateColor.observeOn(AndroidSchedulers.mainThread())
                        .subscribe(expireDateTimeText::setBackgroundColor)
        );
        mRxDisposer.add("createView_onImageThumbnailFileChanged",
                mItemImageDisplay
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemImage -> {
                            if (itemImage.isPresent()) {
                                File file = mItemFileHelper.getItemImageThumbnail(itemImage.get().fileName);
                                imageViewThumbnail.setImageURI(Uri.fromFile(file));
                                imageViewThumbnail.setVisibility(View.VISIBLE);
                            } else {
                                imageViewThumbnail.setVisibility(View.GONE);
                            }
                        })
        );
        mRxDisposer.add("createView_onItemStateChanged",
                mItemState.getSubject()
                        .doOnNext(itemState -> {
                            List<ItemImage> itemImages = itemState.getItemImages();
                            if (!itemImages.isEmpty()) {
                                ItemImage itemImage = itemImages.get(itemImages.size() - 1);
                                mItemImageDisplay.onNext(Optional.of(itemImage));
                            } else {
                                mItemImageDisplay.onNext(Optional.empty());
                            }
                            Date expiredDateTime = itemState.getItemExpiredDateTime();
                            if (expiredDateTime != null) {
                                Instant expiredInstant = expiredDateTime.toInstant();
                                Instant now = Instant.now();
                                Duration difference = Duration.between(now, expiredInstant);
                                long days = difference.toDays();
                                int colorInt = Color.RED;
                                if (days > 1) {
                                    if (days > 14) {
                                        days = 14;
                                    }
                                    float ratio = (255 * ((float) days / 14));
                                    int red = (int) (255 - ratio);
                                    int green = Color.green(mDefaultExpiredDateColor);
                                    int blue = Color.blue(mDefaultExpiredDateColor);
                                    colorInt = Color.rgb(red, green, blue);
                                }
                                mExpiredDateColor.onNext(colorInt);
                            }
                        })
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemState -> {
                            Date expiredDateTime = itemState.getItemExpiredDateTime();
                            if (expiredDateTime != null) {
                                Context context = expireDateTimeText.getContext();
                                Date currentDate = new Date();
                                if (currentDate.after(expiredDateTime)) {
                                    expireDateTimeText.setText(context.getString(R.string.expired_, mDateFormat.format(expiredDateTime)));
                                } else {
                                    String expiredIn;
                                    Instant currentInstant = currentDate.toInstant();
                                    Instant expiredInstant = expiredDateTime.toInstant();
                                    Duration duration = Duration.between(currentInstant, expiredInstant);
                                    long days = duration.toDays();
                                    long hours = duration.toHours();
                                    long min = duration.toMinutes();
                                    if (days != 0) {
                                        expiredIn = context.getString(R.string._days, days);
                                    } else if (hours != 0) {
                                        expiredIn = context.getString(R.string._hours, hours);
                                    } else {
                                        expiredIn = context.getString(R.string._minutes, min);
                                    }
                                    expireDateTimeText.setText(context
                                            .getString(R.string.expired_in_, expiredIn));
                                }
                                expireDateTimeText.setVisibility(View.VISIBLE);
                            } else {
                                expireDateTimeText.setText(null);
                                expireDateTimeText.setVisibility(View.GONE);
                            }
                            nameText.setText(itemState.getItemName());
                            amountText.setText(activity.getString(R.string.amount_, itemState.getItemAmount()));
                            BigDecimal price = itemState.getItemPrice();
                            if (price != null) {
                                Locale locale = activity.getResources().getConfiguration().locale;
                                priceText.setText(activity.getString(R.string.price_, NumberFormat.getInstance(locale)
                                        .format(price)));
                                priceText.setVisibility(View.VISIBLE);
                            } else {
                                priceText.setText(null);
                                priceText.setVisibility(View.GONE);
                            }
                            String barcode = itemState.getItemBarcode();
                            if (barcode != null && !barcode.isEmpty()) {
                                barcodeText.setText(activity.getString(R.string.barcode_, barcode));
                                barcodeText.setVisibility(View.VISIBLE);
                            } else {
                                barcodeText.setVisibility(View.GONE);
                            }

                            Collection<ItemTag> itemTags = itemState.getItemTags();
                            tagDisplayContainer.removeAllViews();
                            if (!itemTags.isEmpty()) {
                                for (ItemTag itemTag : itemTags) {
                                    Chip chip = new Chip(activity);
                                    chip.setText(itemTag.tag);
                                    tagDisplayContainer.addView(chip);
                                }
                                tagDisplayContainer.setVisibility(View.VISIBLE);
                            } else {
                                tagDisplayContainer.setVisibility(View.GONE);
                            }
                        }));
        mRxDisposer.add("createView_onItemStateChanged_updateUsageCount",
                mItemState.getSubject().observeOn(Schedulers.from(mExecutorService))
                        .subscribe(itemState -> {
                            List<ItemUsage> itemUsages = mQueryItemUsageCmd.findItemUsageByItemId(itemState.getItemId()).blockingGet();
                            if (itemUsages != null && !itemUsages.isEmpty()) {
                                int count = itemState.getItemAmount();
                                for (ItemUsage itemUsage : itemUsages) {
                                    count -= itemUsage.amount;
                                }
                                mUsageCount.onNext(count);
                            } else {
                                mUsageCount.onNext(null);
                            }
                        }));
        mRxDisposer.add("createView_onUsageCountChanged", mUsageCount.getSubject()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(optLong -> {
                    if (optLong.isPresent()) {
                        usageCountButton.setText(optLong.get().toString());
                        usageCountButton.setVisibility(View.VISIBLE);
                    } else {
                        usageCountButton.setVisibility(View.GONE);
                    }
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
        if (id == R.id.imageView_thumbnail) {
            mItemImageDisplay.getValue().
                    ifPresent(value -> mNavigator.push(Routes.COMMON_IMAGEVIEW,
                            ImageViewPage.Args.withFile(mItemFileHelper.getItemImage(value.fileName))));
        } else if (id == R.id.button_edit) {
            if (mOnItemEditClicked != null) {
                mOnItemEditClicked.itemItemSv_onItemEditClicked(mItemState.getValue());
            }
        } else if (id == R.id.button_delete) {
            if (mOnItemDeleteClicked != null) {
                mOnItemDeleteClicked.itemItemSv_onItemDeleteClicked(mItemState.getValue());
            }
        } else if (id == R.id.button_more_action) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.getMenuInflater().inflate(R.menu.item_item_more_action, popup.getMenu());
            popup.setOnMenuItemClickListener(this);
            popup.show();
        } else if (id == R.id.button_usage_count) {
            Long itemId = mItemState.getValue().getItemId();
            mNavigator.push(Routes.ITEM_USAGES_PAGE,
                    ItemUsagesPage.Args.with(itemId));
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_item_usage_list) {
            Long itemId = mItemState.getValue().getItemId();
            mNavigator.push(Routes.ITEM_USAGES_PAGE,
                    ItemUsagesPage.Args.with(itemId));
        } else if (id == R.id.menu_item_maintenance_list) {
            Long itemId = mItemState.getValue().getItemId();
            mNavigator.push(Routes.ITEM_MAINTENANCES_PAGE,
                    ItemMaintenancesPage.Args.with(itemId));
        } else if (id == R.id.menu_item_reminder_list) {
            Long itemId = mItemState.getValue().getItemId();
            mNavigator.push(Routes.ITEM_REMINDERS_PAGE,
                    ItemRemindersPage.Args.with(itemId));
        }
        return false;
    }

    public void setItemState(ItemState itemState) {
        mItemState.onNext(itemState);
    }

    public ItemState getItemState() {
        return mItemState.getValue();
    }

    public void setOnItemEditClicked(OnItemEditClicked onItemEditClicked) {
        mOnItemEditClicked = onItemEditClicked;
    }

    public void setOnItemDeleteClicked(OnItemDeleteClicked onItemDeleteClicked) {
        mOnItemDeleteClicked = onItemDeleteClicked;
    }

    public interface OnItemEditClicked {
        void itemItemSv_onItemEditClicked(ItemState itemState);
    }

    public interface OnItemDeleteClicked {
        void itemItemSv_onItemDeleteClicked(ItemState itemState);
    }
}
