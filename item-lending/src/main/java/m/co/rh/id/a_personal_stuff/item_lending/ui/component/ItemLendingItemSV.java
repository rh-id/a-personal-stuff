package m.co.rh.id.a_personal_stuff.item_lending.ui.component;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_personal_stuff.item_lending.R;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.ImageViewPage;
import m.co.rh.id.a_personal_stuff.item_lending.entity.ItemLendingImage;
import m.co.rh.id.a_personal_stuff.item_lending.model.ItemLendingState;
import m.co.rh.id.a_personal_stuff.item_lending.provider.component.ItemLendingFileHelper;
import m.co.rh.id.anavigator.RouteOptions;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemLendingItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient ItemLendingFileHelper mItemLendingFileHelper;
    private transient BehaviorSubject<Optional<ItemLendingImage>> mItemImageDisplay;

    private SerialBehaviorSubject<ItemLendingState> mItemLendingState;
    private DateFormat mDateFormat;

    /**
     * Whether this row renders the compact appearance. Reactive so a mode
     * switch re-applies the matching ConstraintSet to the existing view tree.
     */
    private final SerialBehaviorSubject<Boolean> mCompact;

    private transient OnItemLendingEditClicked mOnItemLendingEditClicked;
    private transient OnItemLendingDeleteClicked mOnItemLendingDeleteClicked;

    public ItemLendingItemSV() {
        this(false);
    }

    public ItemLendingItemSV(boolean compact) {
        mItemLendingState = new SerialBehaviorSubject<>();
        mCompact = new SerialBehaviorSubject<>();
        mCompact.onNext(compact);
        mDateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    }

    public void setCompact(boolean compact) {
        mCompact.onNext(compact);
    }

    public boolean isCompact() {
        Boolean value = mCompact.getValue();
        return value != null && value;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mItemLendingFileHelper = mSvProvider.get(ItemLendingFileHelper.class);
        mItemImageDisplay = BehaviorSubject.create();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        // Single unified layout; compact vs detailed is a ConstraintSet applied
        // to this same view tree, not a different inflation.
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_lending_item, container, false);
        rootLayout.setOnClickListener(this);
        // ConstraintSet operates on the ConstraintLayout child, not the CardView root.
        ConstraintLayout constraintRoot =
                rootLayout.findViewById(R.id.constraint_root);
        ImageView imageViewThumbnail = rootLayout.findViewById(R.id.imageView_thumbnail);
        imageViewThumbnail.setOnClickListener(this);
        TextView lendDateTimeText = rootLayout.findViewById(R.id.text_lend_date_time);
        TextView borrowerText = rootLayout.findViewById(R.id.text_borrower);
        TextView dueText = rootLayout.findViewById(R.id.text_due);
        TextView noteText = rootLayout.findViewById(R.id.text_note);
        TextView statusText = rootLayout.findViewById(R.id.text_status);
        Button editButton = rootLayout.findViewById(R.id.button_edit);
        editButton.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);

        ConstraintSet compactSet = new ConstraintSet();
        compactSet.load(activity, R.xml.item_lending_item_compact_constraints);
        // Clone the detailed constraints from the inflated ConstraintLayout child
        // (the layout root is a CardView, so cloning the live view is unambiguous).
        ConstraintSet detailedSet = new ConstraintSet();
        detailedSet.clone(constraintRoot);
        mRxDisposer.add("createView_onCompactChanged",
                mCompact.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(compact -> {
                            (compact ? compactSet : detailedSet).applyTo(constraintRoot);
                            // button_edit is nested inside the action container, so
                            // ConstraintSet (which targets direct children) can't
                            // reliably toggle it — do it here. In compact mode the
                            // edit button is hidden (edit is via card tap); delete stays.
                            editButton.setVisibility(compact ? View.GONE : View.VISIBLE);
                            // Re-publish so data-driven visibility (e.g. note
                            // hidden when empty) is re-applied after applyTo resets
                            // views from the set's snapshot.
                            ItemLendingState current = mItemLendingState.getValue();
                            if (current != null) {
                                mItemLendingState.onNext(current);
                            }
                        }));

        mRxDisposer.add("createView_onImageThumbnailFileChanged",
                mItemImageDisplay
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemImage -> {
                            if (itemImage.isPresent()) {
                                String fileName = itemImage.get().fileName;
                                File file = mItemLendingFileHelper.getItemLendingImageThumbnail(fileName);
                                imageViewThumbnail.setImageURI(Uri.fromFile(file));
                                imageViewThumbnail.setVisibility(View.VISIBLE);
                                Uri actualImageUri = Uri.fromFile(mItemLendingFileHelper.getItemLendingImage(fileName));
                                imageViewThumbnail.setTransitionName(actualImageUri.toString());
                            } else {
                                imageViewThumbnail.setVisibility(View.GONE);
                                imageViewThumbnail.setTransitionName(null);
                            }
                        })
        );

        Context context = activity.getApplicationContext();
        mRxDisposer.add("createView_onItemLendingStateChanged",
                mItemLendingState.getSubject()
                        .doOnNext(itemLendingState -> {
                            List<ItemLendingImage> itemLendingImages = itemLendingState.getItemLendingImages();
                            if (!itemLendingImages.isEmpty()) {
                                ItemLendingImage itemImage = itemLendingImages.get(itemLendingImages.size() - 1);
                                mItemImageDisplay.onNext(Optional.of(itemImage));
                            } else {
                                mItemImageDisplay.onNext(Optional.empty());
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemLendingState -> {
                            // validation enforces a non-null lend date on
                            // app-created rows; a hand-edited backup can still
                            // carry null, so render empty rather than crash
                            Date lendDateTime = itemLendingState.getLendDateTime();
                            if (lendDateTime != null) {
                                lendDateTimeText.setText(mDateFormat.format(lendDateTime));
                            } else {
                                lendDateTimeText.setText(null);
                            }
                            borrowerText.setText(
                                    context
                                            .getString(R.string.borrower_, itemLendingState.getItemLendingBorrowerName())
                            );
                            Date dueDateTime = itemLendingState.getDueDateTime();
                            if (dueDateTime != null) {
                                dueText.setText(context.getString(R.string.due_, mDateFormat.format(dueDateTime)));
                                dueText.setVisibility(View.VISIBLE);
                            } else {
                                dueText.setText(null);
                                dueText.setVisibility(View.GONE);
                            }
                            String note = itemLendingState.getItemLending().note;
                            if (note != null && !note.isEmpty()) {
                                noteText.setText(
                                        context.getString(R.string.note_,
                                                note)
                                );
                                noteText.setVisibility(View.VISIBLE);
                            } else {
                                noteText.setText(null);
                                noteText.setVisibility(View.GONE);
                            }
                            // Status is display-only and derived at bind time:
                            // Returned (returned set) beats Overdue (open and past
                            // due) beats Lent (open). Like the item expiry badge,
                            // it does not tick on its own — rows refresh on attach.
                            applyStatus(context, statusText, itemLendingState);
                        }));
        return rootLayout;
    }

    /**
     * Applies the resolved lending status to the chip view: label, tinted
     * background, text color and leading icon per state.
     */
    private void applyStatus(Context context, TextView statusText, ItemLendingState itemLendingState) {
        int statusColorRes;
        int statusBgRes;
        int statusIconRes;
        String statusLabel;
        if (itemLendingState.getReturnedDateTime() != null) {
            statusLabel = context.getString(R.string.status_returned);
            statusColorRes = R.color.lending_status_returned;
            statusBgRes = R.drawable.bg_status_returned;
            statusIconRes = R.drawable.ic_status_returned;
        } else {
            Date dueDateTime = itemLendingState.getDueDateTime();
            if (dueDateTime != null && dueDateTime.before(new Date())) {
                statusLabel = context.getString(R.string.status_overdue);
                statusColorRes = R.color.lending_status_overdue;
                statusBgRes = R.drawable.bg_status_overdue;
                statusIconRes = R.drawable.ic_status_overdue;
            } else {
                statusLabel = context.getString(R.string.status_lent);
                statusColorRes = R.color.lending_status_lent;
                statusBgRes = R.drawable.bg_status_lent;
                statusIconRes = R.drawable.ic_status_lent;
            }
        }
        statusText.setText(statusLabel);
        statusText.setBackgroundResource(statusBgRes);
        statusText.setTextColor(ContextCompat.getColor(context, statusColorRes));
        // ContextCompat for vector-drawable-on-API-21 compat
        statusText.setCompoundDrawablesWithIntrinsicBounds(
                ContextCompat.getDrawable(context, statusIconRes), null, null, null);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.card_root || id == R.id.button_edit) {
            if (mOnItemLendingEditClicked != null) {
                mOnItemLendingEditClicked.itemLendingItemSV_onItemLendingEditClicked(mItemLendingState.getValue());
            }
        } else if (id == R.id.imageView_thumbnail) {
            List<ItemLendingImage> itemLendingImages = mItemLendingState.getValue().getItemLendingImages();
            ArrayList<File> imageFiles = new ArrayList<>();
            for (ItemLendingImage itemLendingImage : itemLendingImages) {
                imageFiles.add(mItemLendingFileHelper.getItemLendingImage(itemLendingImage.fileName));
            }
            if (!imageFiles.isEmpty()) {
                int startIndex = imageFiles.size() - 1;
                mNavigator.push(Routes.COMMON_IMAGEVIEW,
                        ImageViewPage.Args.withFiles(imageFiles, startIndex),
                        null,
                        RouteOptions.withTransition(
                                m.co.rh.id.a_personal_stuff.base.R.transition.page_imageview_enter,
                                m.co.rh.id.a_personal_stuff.base.R.transition.page_imageview_exit));
            }
        } else if (id == R.id.button_delete) {
            if (mOnItemLendingDeleteClicked != null) {
                mOnItemLendingDeleteClicked.itemLendingItemSV_onItemLendingDeleteClicked(mItemLendingState.getValue());
            }
        }
    }

    public void setItemLendingState(ItemLendingState itemLendingState) {
        mItemLendingState.onNext(itemLendingState);
    }

    public void setOnItemLendingEditClicked(OnItemLendingEditClicked onItemLendingEditClicked) {
        mOnItemLendingEditClicked = onItemLendingEditClicked;
    }

    public void setOnItemLendingDeleteClicked(OnItemLendingDeleteClicked onItemLendingDeleteClicked) {
        mOnItemLendingDeleteClicked = onItemLendingDeleteClicked;
    }

    public ItemLendingState getItemLendingState() {
        return mItemLendingState.getValue();
    }

    public interface OnItemLendingEditClicked {
        void itemLendingItemSV_onItemLendingEditClicked(ItemLendingState itemLendingState);
    }

    public interface OnItemLendingDeleteClicked {
        void itemLendingItemSV_onItemLendingDeleteClicked(ItemLendingState itemLendingState);
    }
}
