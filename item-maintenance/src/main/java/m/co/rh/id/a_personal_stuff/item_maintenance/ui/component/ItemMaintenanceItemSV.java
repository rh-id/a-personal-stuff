package m.co.rh.id.a_personal_stuff.item_maintenance.ui.component;

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
import java.util.Optional;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.ImageViewPage;
import m.co.rh.id.a_personal_stuff.item_maintenance.R;
import m.co.rh.id.a_personal_stuff.item_maintenance.entity.ItemMaintenanceImage;
import m.co.rh.id.a_personal_stuff.item_maintenance.model.ItemMaintenanceState;
import m.co.rh.id.a_personal_stuff.item_maintenance.provider.component.ItemMaintenanceFileHelper;
import m.co.rh.id.anavigator.RouteOptions;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class ItemMaintenanceItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {

    @NavInject
    private transient INavigator mNavigator;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient ItemMaintenanceFileHelper mItemMaintenanceFileHelper;
    private transient BehaviorSubject<Optional<ItemMaintenanceImage>> mItemImageDisplay;

    private SerialBehaviorSubject<ItemMaintenanceState> mItemMaintenanceState;
    private DateFormat mDateFormat;

    /**
     * Whether this row renders the compact appearance. Reactive so a mode
     * switch re-applies the matching ConstraintSet to the existing view tree.
     */
    private final SerialBehaviorSubject<Boolean> mCompact;

    private transient OnItemMaintenanceEditClicked mOnItemMaintenanceEditClicked;
    private transient OnItemMaintenanceDeleteClicked mOnItemMaintenanceDeleteClicked;

    public ItemMaintenanceItemSV() {
        this(false);
    }

    public ItemMaintenanceItemSV(boolean compact) {
        mItemMaintenanceState = new SerialBehaviorSubject<>();
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
        mItemMaintenanceFileHelper = mSvProvider.get(ItemMaintenanceFileHelper.class);
        mItemImageDisplay = BehaviorSubject.create();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        // Single unified layout; compact vs detailed is a ConstraintSet applied
        // to this same view tree, not a different inflation.
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.item_maintenance_item, container, false);
        rootLayout.setOnClickListener(this);
        // ConstraintSet operates on the ConstraintLayout child, not the CardView root.
        ConstraintLayout constraintRoot =
                rootLayout.findViewById(R.id.constraint_root);
        TextView maintenanceDateTimeText = rootLayout.findViewById(R.id.text_maintenance_date_time);
        TextView costText = rootLayout.findViewById(R.id.text_cost);
        TextView descriptionText = rootLayout.findViewById(R.id.text_description);
        ImageView imageViewThumbnail = rootLayout.findViewById(R.id.imageView_thumbnail);
        imageViewThumbnail.setOnClickListener(this);
        Button editButton = rootLayout.findViewById(R.id.button_edit);
        editButton.setOnClickListener(this);
        Button deleteButton = rootLayout.findViewById(R.id.button_delete);
        deleteButton.setOnClickListener(this);

        ConstraintSet compactSet = new ConstraintSet();
        compactSet.load(activity, R.xml.item_maintenance_item_compact_constraints);
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
                            // Re-publish so data-driven visibility (e.g. description
                            // hidden when empty) is re-applied after applyTo resets
                            // views from the set's snapshot.
                            ItemMaintenanceState current = mItemMaintenanceState.getValue();
                            if (current != null) {
                                mItemMaintenanceState.onNext(current);
                            }
                        }));

        mRxDisposer.add("createView_onImageThumbnailFileChanged",
                mItemImageDisplay
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemImage -> {
                            if (itemImage.isPresent()) {
                                String fileName = itemImage.get().fileName;
                                File file = mItemMaintenanceFileHelper.getItemMaintenanceImageThumbnail(fileName);
                                imageViewThumbnail.setImageURI(Uri.fromFile(file));
                                imageViewThumbnail.setVisibility(View.VISIBLE);
                                Uri actualImageUri = Uri.fromFile(mItemMaintenanceFileHelper.getItemMaintenanceImage(fileName));
                                imageViewThumbnail.setTransitionName(actualImageUri.toString());
                            } else {
                                imageViewThumbnail.setVisibility(View.GONE);
                                imageViewThumbnail.setTransitionName(null);
                            }
                        })
        );

        Context context = activity.getApplicationContext();
        mRxDisposer.add("createView_onItemMaintenanceStateChanged",
                mItemMaintenanceState.getSubject()
                        .doOnNext(itemMaintenanceState -> {
                            ArrayList<ItemMaintenanceImage> itemMaintenanceImages = itemMaintenanceState.getItemMaintenanceImages();
                            if (!itemMaintenanceImages.isEmpty()) {
                                ItemMaintenanceImage itemImage = itemMaintenanceImages.get(itemMaintenanceImages.size() - 1);
                                mItemImageDisplay.onNext(Optional.of(itemImage));
                            } else {
                                mItemImageDisplay.onNext(Optional.empty());
                            }
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemMaintenanceState -> {
                            maintenanceDateTimeText.setText(mDateFormat.format(itemMaintenanceState.getItemMaintenanceDateTime()));
                            costText.setText(
                                    context
                                            .getString(R.string.cost_, itemMaintenanceState.getItemMaintenanceCost())
                            );
                            String description = itemMaintenanceState.getItemMaintenanceDescription();
                            if (description != null && !description.isEmpty()) {
                                descriptionText.setText(
                                        context.getString(R.string.description_,
                                                description)
                                );
                                descriptionText.setVisibility(View.VISIBLE);
                            } else {
                                descriptionText.setText(null);
                                descriptionText.setVisibility(View.GONE);
                            }
                        }));
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.card_root || id == R.id.button_edit) {
            if (mOnItemMaintenanceEditClicked != null) {
                mOnItemMaintenanceEditClicked.itemMaintenanceItemSV_onItemMaintenanceEditClicked(mItemMaintenanceState.getValue());
            }
        } else if (id == R.id.imageView_thumbnail) {
            ArrayList<ItemMaintenanceImage> itemMaintenanceImages = mItemMaintenanceState.getValue().getItemMaintenanceImages();
            ArrayList<File> imageFiles = new ArrayList<>();
            for (ItemMaintenanceImage itemMaintenanceImage : itemMaintenanceImages) {
                imageFiles.add(mItemMaintenanceFileHelper.getItemMaintenanceImage(itemMaintenanceImage.fileName));
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
            if (mOnItemMaintenanceDeleteClicked != null) {
                mOnItemMaintenanceDeleteClicked.itemMaintenanceItemSV_onItemMaintenanceDeleteClicked(mItemMaintenanceState.getValue());
            }
        }
    }

    public void setItemMaintenanceState(ItemMaintenanceState itemMaintenanceState) {
        mItemMaintenanceState.onNext(itemMaintenanceState);
    }

    public void setOnItemMaintenanceEditClicked(OnItemMaintenanceEditClicked onItemMaintenanceDeleteClicked) {
        mOnItemMaintenanceEditClicked = onItemMaintenanceDeleteClicked;
    }

    public void setOnItemMaintenanceDeleteClicked(OnItemMaintenanceDeleteClicked onItemMaintenanceDeleteClicked) {
        mOnItemMaintenanceDeleteClicked = onItemMaintenanceDeleteClicked;
    }

    public ItemMaintenanceState getItemMaintenanceState() {
        return mItemMaintenanceState.getValue();
    }

    public interface OnItemMaintenanceEditClicked {
        void itemMaintenanceItemSV_onItemMaintenanceEditClicked(ItemMaintenanceState itemMaintenanceState);
    }

    public interface OnItemMaintenanceDeleteClicked {
        void itemMaintenanceItemSV_onItemMaintenanceDeleteClicked(ItemMaintenanceState itemMaintenanceState);
    }
}
