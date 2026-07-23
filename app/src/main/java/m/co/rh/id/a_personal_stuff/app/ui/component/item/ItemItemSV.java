package m.co.rh.id.a_personal_stuff.app.ui.component.item;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.os.ConfigurationCompat;

import com.google.android.material.chip.Chip;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
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
import m.co.rh.id.anavigator.RouteOptions;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

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
    private transient OnItemDuplicateClicked mOnItemDuplicateClicked;

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
        rootLayout.setOnClickListener(this);
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
                                String fileName = itemImage.get().fileName;
                                File file = mItemFileHelper.getItemImageThumbnail(fileName);
                                imageViewThumbnail.setImageURI(Uri.fromFile(file));
                                imageViewThumbnail.setVisibility(View.VISIBLE);
                                Uri actualImageUri = Uri.fromFile(mItemFileHelper.getItemImage(fileName));
                                imageViewThumbnail.setTransitionName(actualImageUri.toString());
                            } else {
                                imageViewThumbnail.setVisibility(View.GONE);
                                imageViewThumbnail.setTransitionName(null);
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
                                Locale locale = ConfigurationCompat.getLocales(activity.getResources().getConfiguration()).get(0);
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
        if (id == R.id.card_root || id == R.id.button_edit) {
            if (mOnItemEditClicked != null) {
                mOnItemEditClicked.itemItemSv_onItemEditClicked(mItemState.getValue());
            }
        } else if (id == R.id.imageView_thumbnail) {
            // Pass the item's full image list so the viewer can page through
            // every image, not just the single thumbnail shown in the row.
            // The currently-displayed thumbnail is the last image in the list
            // (see createView_onItemStateChanged), so start there.
            List<ItemImage> itemImages = mItemState.getValue().getItemImages();
            ArrayList<File> imageFiles = new ArrayList<>();
            for (ItemImage itemImage : itemImages) {
                imageFiles.add(mItemFileHelper.getItemImage(itemImage.fileName));
            }
            if (!imageFiles.isEmpty()) {
                int startIndex = imageFiles.size() - 1;
                mNavigator.push(Routes.COMMON_IMAGEVIEW,
                        ImageViewPage.Args.withFiles(imageFiles, startIndex)
                        , null,
                        RouteOptions.withTransition(m.co.rh.id.a_personal_stuff.base.R.transition.page_imageview_enter,
                                m.co.rh.id.a_personal_stuff.base.R.transition.page_imageview_exit)
                );
            }
        } else if (id == R.id.button_delete) {
            if (mOnItemDeleteClicked != null) {
                mOnItemDeleteClicked.itemItemSv_onItemDeleteClicked(mItemState.getValue());
            }
        } else if (id == R.id.button_more_action) {
            showMoreActionList(view);
        } else if (id == R.id.button_usage_count) {
            Long itemId = mItemState.getValue().getItemId();
            mNavigator.push(Routes.ITEM_USAGES_PAGE,
                    ItemUsagesPage.Args.with(itemId));
        }
    }

    private void showMoreActionList(View anchor) {
        Context context = anchor.getContext();
        Object[] items = new Object[]{
                context.getString(m.co.rh.id.a_personal_stuff.item_usage.R.string.title_usages),
                context.getString(m.co.rh.id.a_personal_stuff.item_maintenance.R.string.title_maintenances),
                context.getString(m.co.rh.id.a_personal_stuff.item_reminder.R.string.title_reminders),
                DIVIDER,
                context.getString(R.string.title_duplicate),
        };
        int[] actionIds = new int[]{
                R.id.menu_item_usage_list,
                R.id.menu_item_maintenance_list,
                R.id.menu_item_reminder_list,
                R.id.menu_item_duplicate,
        };
        ListPopupWindow listPopupWindow = new ListPopupWindow(context);
        MoreActionAdapter adapter = new MoreActionAdapter(context, items);
        listPopupWindow.setAdapter(adapter);
        listPopupWindow.setAnchorView(anchor);
        // Drop down below the anchor, start-aligned — matches the old PopupMenu.
        listPopupWindow.setDropDownGravity(android.view.Gravity.START | android.view.Gravity.TOP);
        listPopupWindow.setHeight(ListPopupWindow.WRAP_CONTENT);
        // Measure the widest text row so the popup fits the longest label
        // (simple_list_item_1 is match_parent-width, which the ListPopupWindow
        // internal measure pass collapses to ~0; setContentWidth forces a sane width).
        listPopupWindow.setContentWidth(measureMaxContentWidth(context, adapter));
        listPopupWindow.setOnItemClickListener((parent, view, position, id1) -> {
            int actionIndex = adapter.actionIndexForPosition(position);
            if (actionIndex >= 0 && actionIndex < actionIds.length) {
                handleMoreAction(actionIds[actionIndex]);
            }
            listPopupWindow.dismiss();
        });
        listPopupWindow.show();
    }

    private static int measureMaxContentWidth(Context context, MoreActionAdapter adapter) {
        int maxWidth = 0;
        View measureView = null;
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItemViewType(i) != 0) {
                continue;
            }
            measureView = adapter.getView(i, measureView, null);
            // getView inflates simple_list_item_1 with a null parent, so the view
            // has no LayoutParams — TextView.checkForRelayout() crashes on setText
            // without one. Assign one before measuring.
            if (measureView.getLayoutParams() == null) {
                measureView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            measureView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int w = measureView.getMeasuredWidth();
            if (w > maxWidth) {
                maxWidth = w;
            }
        }
        // Add horizontal padding so labels aren't flush against the popup edges.
        float density = context.getResources().getDisplayMetrics().density;
        return maxWidth + (int) (32 * density);
    }

    private static final Object DIVIDER = new Object();

    private void handleMoreAction(int id) {
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
        } else if (id == R.id.menu_item_duplicate) {
            if (mOnItemDuplicateClicked != null) {
                mOnItemDuplicateClicked.itemItemSv_onItemDuplicateClicked(mItemState.getValue());
            }
        }
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

    public void setOnItemDuplicateClicked(OnItemDuplicateClicked onItemDuplicateClicked) {
        mOnItemDuplicateClicked = onItemDuplicateClicked;
    }

    public interface OnItemEditClicked {
        void itemItemSv_onItemEditClicked(ItemState itemState);
    }

    public interface OnItemDeleteClicked {
        void itemItemSv_onItemDeleteClicked(ItemState itemState);
    }

    public interface OnItemDuplicateClicked {
        void itemItemSv_onItemDuplicateClicked(ItemState itemState);
    }

    /**
     * Adapter backing the more-actions popup. Supports two view types: a
     * standard text row ({@code android.R.layout.simple_list_item_1}, which is
     * pre-styled to be readable in a {@link ListPopupWindow}) and a divider
     * row (a 1dp hairline) used to separate the Duplicate action.
     */
    private static class MoreActionAdapter extends BaseAdapter {
        private static final int VIEW_TYPE_TEXT = 0;
        private static final int VIEW_TYPE_DIVIDER = 1;

        private final Context mContext;
        private final Object[] mItems;

        MoreActionAdapter(Context context, Object[] items) {
            mContext = context;
            mItems = items;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return mItems[position] == DIVIDER ? VIEW_TYPE_DIVIDER : VIEW_TYPE_TEXT;
        }

        @Override
        public boolean isEnabled(int position) {
            return mItems[position] != DIVIDER;
        }

        @Override
        public int getCount() {
            return mItems.length;
        }

        @Override
        public Object getItem(int position) {
            return mItems[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        /**
         * Map a list position to the index of the corresponding action id in
         * the caller's actionIds array, skipping divider rows.
         */
        int actionIndexForPosition(int position) {
            int actionIndex = -1;
            for (int i = 0; i <= position; i++) {
                if (mItems[i] != DIVIDER) {
                    actionIndex++;
                }
            }
            return actionIndex;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (getItemViewType(position) == VIEW_TYPE_DIVIDER) {
                if (convertView == null) {
                    View divider = new View(mContext);
                    divider.setLayoutParams(new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 1));
                    TypedValue outValue = new TypedValue();
                    mContext.getTheme().resolveAttribute(
                            android.R.attr.listDivider, outValue, true);
                    divider.setBackgroundResource(outValue.resourceId);
                    convertView = divider;
                }
                return convertView;
            }
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext)
                        .inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            ((TextView) convertView).setText((String) mItems[position]);
            return convertView;
        }
    }
}
