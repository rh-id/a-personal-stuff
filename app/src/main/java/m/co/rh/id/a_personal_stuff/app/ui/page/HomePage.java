package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.os.ConfigurationCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.command.DashboardCmd;
import m.co.rh.id.a_personal_stuff.app.provider.command.QueryItemCmd;
import m.co.rh.id.a_personal_stuff.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_personal_stuff.app.ui.model.DashboardData;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.app.provider.command.ExportCmd;
import m.co.rh.id.a_personal_stuff.app.provider.command.ExportSpreadsheetCmd;
import m.co.rh.id.a_personal_stuff.app.provider.command.ImportCmd;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.base.ui.page.common.ProgressSVDialog;
import m.co.rh.id.a_personal_stuff.base.util.UiUtils;
import m.co.rh.id.a_personal_stuff.item_maintenance.ui.page.ItemMaintenanceDetailPage;
import m.co.rh.id.a_personal_stuff.item_purchase.ui.page.ItemPurchaseDetailPage;
import m.co.rh.id.a_personal_stuff.item_reminder.entity.ItemReminder;
import m.co.rh.id.a_personal_stuff.item_reminder.ui.page.ItemReminderDetailPage;
import m.co.rh.id.a_personal_stuff.item_usage.ui.page.ItemUsageDetailPage;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class HomePage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnBackPressed<Activity>, NavOnActivityResult<Activity>, DrawerLayout.DrawerListener, View.OnClickListener {
    private static final String TAG = HomePage.class.getName();
    private static final int REQUEST_CODE_IMPORT = 1001;

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    private boolean mIsDrawerOpen;
    private transient long mLastBackPressMilis;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient AppNotificationHandler mAppNotificationHandler;
    private transient RxDisposer mRxDisposer;
    private transient QueryItemCmd mQueryItemCmd;
    private transient DashboardCmd mDashboardCmd;
    private transient ExportCmd mExportCmd;
    private transient ExportSpreadsheetCmd mExportSpreadsheetCmd;
    private transient ImportCmd mImportCmd;
    private transient FileHelper mFileHelper;
    private transient ILogger mLogger;

    private transient DrawerLayout mDrawerLayout;
    private transient View.OnClickListener mOnNavigationClicked;
    private transient Button mButtonExport;
    private transient Button mButtonImport;
    private transient Button mButtonExportSpreadsheet;
    private transient View mCardDashboardInventory;
    private transient View mCardDashboardReminders;
    private transient TextView mTextDashboardInventoryCount;
    private transient TextView mTextDashboardInventoryValue;
    private transient View mContainerDashboardLowStock;
    private transient View mContainerDashboardExpiring;
    private transient View mContainerDashboardExpiredGroup;
    private transient View mContainerDashboardExpiringGroup;
    private transient View mDividerDashboardLowStock;
    private transient View mDividerDashboardExpiring;
    private transient TextView mTextDashboardExpiringSummary;
    private transient TextView mTextDashboardExpiredSamples;
    private transient TextView mTextDashboardExpiringSoonSummary;
    private transient TextView mTextDashboardExpiringSamples;
    private transient TextView mTextDashboardLowStockSummary;
    private transient TextView mTextDashboardLowStockSamples;
    private transient TextView mTextDashboardRemindersSummary;
    private transient TextView mTextDashboardRemindersSamples;
    private transient Locale mLocale;

    public HomePage() {
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mAppNotificationHandler = mSvProvider.get(AppNotificationHandler.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mQueryItemCmd = mSvProvider.get(QueryItemCmd.class);
        mDashboardCmd = mSvProvider.get(DashboardCmd.class);
        mExportCmd = mSvProvider.get(ExportCmd.class);
        mExportSpreadsheetCmd = mSvProvider.get(ExportSpreadsheetCmd.class);
        mImportCmd = mSvProvider.get(ImportCmd.class);
        mFileHelper = mSvProvider.get(FileHelper.class);
        mLogger = mSvProvider.get(ILogger.class);
        mOnNavigationClicked = view -> {
            if (!mDrawerLayout.isOpen()) {
                mDrawerLayout.open();
            }
        };
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_home, container, false);
        View menuItems = rootLayout.findViewById(R.id.menu_items);
        menuItems.setOnClickListener(this);
        View menuItemChecklists = rootLayout.findViewById(R.id.menu_item_checklists);
        menuItemChecklists.setOnClickListener(this);
        View menuSettings = rootLayout.findViewById(R.id.menu_settings);
        menuSettings.setOnClickListener(this);
        View menuDonation = rootLayout.findViewById(R.id.menu_donation);
        menuDonation.setOnClickListener(this);
        mDrawerLayout = rootLayout.findViewById(R.id.drawer);
        mDrawerLayout.addDrawerListener(this);
        mAppBarSV.setTitle(activity.getString(R.string.home));
        mAppBarSV.setNavigationOnClick(mOnNavigationClicked);
        if (mIsDrawerOpen) {
            mDrawerLayout.open();
        }
        Button addItemButton = rootLayout.findViewById(R.id.button_add_item);
        addItemButton.setOnClickListener(this);
        Button addItemUsageButton = rootLayout.findViewById(R.id.button_add_item_usage);
        addItemUsageButton.setOnClickListener(this);
        Button addItemPurchaseButton = rootLayout.findViewById(R.id.button_add_item_purchase);
        addItemPurchaseButton.setOnClickListener(this);
        Button addItemMaintenanceButton = rootLayout.findViewById(R.id.button_add_item_maintenance);
        addItemMaintenanceButton.setOnClickListener(this);
        Button addItemReminderButton = rootLayout.findViewById(R.id.button_add_item_reminder);
        addItemReminderButton.setOnClickListener(this);
        Button addItemChecklistButton = rootLayout.findViewById(R.id.button_add_item_checklist);
        addItemChecklistButton.setOnClickListener(this);
        mButtonExport = rootLayout.findViewById(R.id.button_export);
        mButtonImport = rootLayout.findViewById(R.id.button_import);
        if (mButtonExport != null) {
            mButtonExport.setOnClickListener(this);
        }
        if (mButtonImport != null) {
            mButtonImport.setOnClickListener(this);
        }
        mButtonExportSpreadsheet = rootLayout.findViewById(R.id.button_export_spreadsheet);
        if (mButtonExportSpreadsheet != null) {
            mButtonExportSpreadsheet.setOnClickListener(this);
            if (!ExportSpreadsheetCmd.isSupported()) {
                mButtonExportSpreadsheet.setVisibility(View.GONE);
            }
        }
        // A rebuild (rotation/theme change) must never start or re-subscribe an
        // export: the running export's original subscriptions keep delivering
        // progress into the shown dialog's Args subject and the result to this
        // view, because rebuilds reuse the same command, disposers and
        // activity, and the dialog re-binds the subject's latest value. Only
        // reflect the in-flight state.
        if (mExportSpreadsheetCmd.isExporting()) {
            showExportingState();
        }
        mLocale = ConfigurationCompat.getLocales(activity.getResources().getConfiguration()).get(0);
        mCardDashboardInventory = rootLayout.findViewById(R.id.card_dashboard_inventory);
        mCardDashboardInventory.setOnClickListener(this);
        mCardDashboardReminders = rootLayout.findViewById(R.id.card_dashboard_reminders);
        mCardDashboardReminders.setOnClickListener(this);
        mTextDashboardInventoryCount = rootLayout.findViewById(R.id.text_dashboard_inventory_count);
        mTextDashboardInventoryValue = rootLayout.findViewById(R.id.text_dashboard_inventory_value);
        mContainerDashboardLowStock = rootLayout.findViewById(R.id.container_dashboard_low_stock);
        mContainerDashboardLowStock.setOnClickListener(this);
        mContainerDashboardExpiring = rootLayout.findViewById(R.id.container_dashboard_expiring);
        mContainerDashboardExpiring.setOnClickListener(this);
        mContainerDashboardExpiredGroup = rootLayout.findViewById(R.id.container_dashboard_expired_group);
        mContainerDashboardExpiringGroup = rootLayout.findViewById(R.id.container_dashboard_expiring_group);
        mDividerDashboardLowStock = rootLayout.findViewById(R.id.divider_dashboard_low_stock);
        mDividerDashboardExpiring = rootLayout.findViewById(R.id.divider_dashboard_expiring);
        mTextDashboardExpiringSummary = rootLayout.findViewById(R.id.text_dashboard_expiring_summary);
        mTextDashboardExpiredSamples = rootLayout.findViewById(R.id.text_dashboard_expired_samples);
        mTextDashboardExpiringSoonSummary = rootLayout.findViewById(R.id.text_dashboard_expiring_soon_summary);
        mTextDashboardExpiringSamples = rootLayout.findViewById(R.id.text_dashboard_expiring_samples);
        mTextDashboardLowStockSummary = rootLayout.findViewById(R.id.text_dashboard_low_stock_summary);
        mTextDashboardLowStockSamples = rootLayout.findViewById(R.id.text_dashboard_low_stock_samples);
        mTextDashboardRemindersSummary = rootLayout.findViewById(R.id.text_dashboard_reminders_summary);
        mTextDashboardRemindersSamples = rootLayout.findViewById(R.id.text_dashboard_reminders_samples);
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        // An item can cross its expiry boundary (or a reminder pass) while the
        // process is alive without a notifier event, so recompute on each attach
        mDashboardCmd.refresh();
        mRxDisposer.add("createView_onNotificationEvent",
                mAppNotificationHandler.getItemReminderFlow()
                        .map(itemReminder -> mQueryItemCmd
                                .findItemStateByItemId(itemReminder.itemId)
                                .blockingGet())
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(itemState -> {
                            NavRoute currentRoute = mNavigator.getCurrentRoute();
                            if (Routes.ITEMS_PAGE.equals(currentRoute.getRouteName())) {
                                mNavigator.push(Routes.ITEM_DETAIL_PAGE,
                                        ItemDetailPage.Args.forUpdate(itemState));
                            } else {
                                mNavigator.push(Routes.ITEMS_PAGE,
                                        ItemsPage.Args.showItem(itemState.getItemId()));
                            }
                        }));
        mRxDisposer.add("createView_onInventoryAlert",
                mAppNotificationHandler.getInventoryAlertFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(requestId -> {
                            NavRoute currentRoute = mNavigator.getCurrentRoute();
                            if (!Routes.ITEMS_PAGE.equals(currentRoute.getRouteName())) {
                                mNavigator.push(Routes.ITEMS_PAGE);
                            }
                        }));
        mRxDisposer.add("createView_onDashboardData",
                mDashboardCmd.getDashboardFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::renderDashboard));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mDrawerLayout = null;
        mOnNavigationClicked = null;
        mButtonExport = null;
        mButtonImport = null;
        mButtonExportSpreadsheet = null;
        mCardDashboardInventory = null;
        mCardDashboardReminders = null;
        mTextDashboardInventoryCount = null;
        mTextDashboardInventoryValue = null;
        mContainerDashboardLowStock = null;
        mContainerDashboardExpiring = null;
        mContainerDashboardExpiredGroup = null;
        mContainerDashboardExpiringGroup = null;
        mDividerDashboardLowStock = null;
        mDividerDashboardExpiring = null;
        mTextDashboardExpiringSummary = null;
        mTextDashboardExpiredSamples = null;
        mTextDashboardExpiringSoonSummary = null;
        mTextDashboardExpiringSamples = null;
        mTextDashboardLowStockSummary = null;
        mTextDashboardLowStockSamples = null;
        mTextDashboardRemindersSummary = null;
        mTextDashboardRemindersSamples = null;
        mLocale = null;
    }

    @Override
    public void onBackPressed(View currentView, Activity activity, INavigator navigator) {
        if (mDrawerLayout.isOpen()) {
            mDrawerLayout.close();
        } else {
            long currentMilis = System.currentTimeMillis();
            if ((currentMilis - mLastBackPressMilis) < 1000) {
                navigator.finishActivity(null);
            } else {
                mLastBackPressMilis = currentMilis;
                mSvProvider.get(ILogger.class).i(TAG,
                        activity.getString(R.string.toast_back_press_exit));
            }
        }
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator navigator, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                performImport(activity, uri);
            }
        }
    }

    @Override
    public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
    }

    @Override
    public void onDrawerOpened(@NonNull View drawerView) {
        mIsDrawerOpen = true;
    }

    @Override
    public void onDrawerClosed(@NonNull View drawerView) {
        mIsDrawerOpen = false;
    }

    @Override
    public void onDrawerStateChanged(int newState) {
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.menu_items) {
            mNavigator.push(Routes.ITEMS_PAGE);
        } else if (id == R.id.menu_item_checklists) {
            mNavigator.push(Routes.ITEM_CHECKLISTS_PAGE);
        } else if (id == R.id.menu_settings) {
            mNavigator.push(Routes.SETTINGS_PAGE);
        } else if (id == R.id.menu_donation) {
            mNavigator.push(Routes.DONATIONS_PAGE);
        } else if (id == R.id.button_add_item) {
            mNavigator.push(Routes.ITEM_DETAIL_PAGE);
        } else if (id == R.id.button_add_item_usage) {
            mNavigator.push(Routes.ITEM_SELECT_PAGE,
                    (navigator, navRoute, activity, currentView) -> itemSelectedForUsage(navRoute));
        } else if (id == R.id.button_add_item_purchase) {
            mNavigator.push(Routes.ITEM_SELECT_PAGE,
                    (navigator, navRoute, activity, currentView) -> itemSelectedForPurchase(navRoute));
        } else if (id == R.id.button_add_item_maintenance) {
            mNavigator.push(Routes.ITEM_SELECT_PAGE,
                    (navigator, navRoute, activity, currentView) -> itemSelectedForMaintenance(navRoute));
        } else if (id == R.id.button_add_item_reminder) {
            mNavigator.push(Routes.ITEM_SELECT_PAGE,
                    (navigator, navRoute, activity, currentView) -> itemSelectedForReminder(navRoute));
        } else if (id == R.id.button_add_item_checklist) {
            mNavigator.push(Routes.ITEM_CHECKLIST_ADD_PAGE);
        } else if (id == R.id.button_export) {
            doExport((Activity) view.getContext());
        } else if (id == R.id.button_export_spreadsheet) {
            doExportSpreadsheet((Activity) view.getContext());
        } else if (id == R.id.button_import) {
            doImport((Activity) view.getContext());
        } else if (id == R.id.card_dashboard_inventory
                || id == R.id.container_dashboard_low_stock
                || id == R.id.container_dashboard_expiring) {
            mNavigator.push(Routes.ITEMS_PAGE);
        } else if (id == R.id.card_dashboard_reminders) {
            mNavigator.push(Routes.ITEM_REMINDERS_PAGE);
        }
    }

    private void renderDashboard(DashboardData dashboardData) {
        // the flow may still deliver after this view is disposed (the
        // subscription is only replaced on the next createView)
        if (mCardDashboardInventory == null) {
            return;
        }
        Context context = mCardDashboardInventory.getContext();
        mTextDashboardInventoryCount.setText(String.valueOf(dashboardData.totalItems));
        if (dashboardData.inventoryValue != null) {
            NumberFormat numberFormat = NumberFormat.getNumberInstance(mLocale);
            numberFormat.setMaximumFractionDigits(2);
            mTextDashboardInventoryValue.setText(
                    numberFormat.format(dashboardData.inventoryValue));
        } else {
            mTextDashboardInventoryValue.setText("-");
        }
        int expiringTotal = dashboardData.expiredCount + dashboardData.expiringCount;
        if (expiringTotal > 0) {
            mTextDashboardExpiringSummary.setText(
                    String.valueOf(dashboardData.expiredCount));
            mTextDashboardExpiredSamples.setText(
                    joinExpiringSamples(dashboardData.expiredSamples));
            mTextDashboardExpiringSoonSummary.setText(
                    String.valueOf(dashboardData.expiringCount));
            mTextDashboardExpiringSamples.setText(
                    joinExpiringSamples(dashboardData.expiringSamples));
            mContainerDashboardExpiring.setVisibility(View.VISIBLE);
        } else {
            mContainerDashboardExpiring.setVisibility(View.GONE);
        }
        if (dashboardData.expiredCount > 0) {
            mContainerDashboardExpiredGroup.setVisibility(View.VISIBLE);
        } else {
            mContainerDashboardExpiredGroup.setVisibility(View.GONE);
        }
        if (dashboardData.expiringCount > 0) {
            mContainerDashboardExpiringGroup.setVisibility(View.VISIBLE);
        } else {
            mContainerDashboardExpiringGroup.setVisibility(View.GONE);
        }
        if (dashboardData.lowStockCount > 0) {
            mTextDashboardLowStockSummary.setText(
                    String.valueOf(dashboardData.lowStockCount));
            mTextDashboardLowStockSamples.setText(
                    joinLowStockSamples(context, dashboardData.lowStockSamples));
            mContainerDashboardLowStock.setVisibility(View.VISIBLE);
            mDividerDashboardLowStock.setVisibility(View.VISIBLE);
        } else {
            mContainerDashboardLowStock.setVisibility(View.GONE);
            mDividerDashboardLowStock.setVisibility(View.GONE);
        }
        if (dashboardData.lowStockCount > 0 && expiringTotal > 0) {
            mDividerDashboardExpiring.setVisibility(View.VISIBLE);
        } else {
            mDividerDashboardExpiring.setVisibility(View.GONE);
        }
        if (dashboardData.upcomingReminders.isEmpty()) {
            mTextDashboardRemindersSummary.setText(
                    context.getString(R.string.dashboard_no_upcoming_reminders));
            mTextDashboardRemindersSamples.setText("");
            mCardDashboardReminders.setVisibility(View.GONE);
        } else {
            mTextDashboardRemindersSummary.setText(context.getString(
                    R.string.dashboard_upcoming_reminders_,
                    dashboardData.upcomingReminderCount));
            mTextDashboardRemindersSamples.setText(
                    joinReminderSamples(dashboardData.upcomingReminders));
            mCardDashboardReminders.setVisibility(View.VISIBLE);
        }
    }

    private String joinExpiringSamples(List<Item> items) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM", mLocale);
        List<String> samples = new ArrayList<>();
        if (items != null) {
            for (Item item : items) {
                if (item.expiredDateTime != null) {
                    samples.add(item.name + " — " + dateFormat.format(item.expiredDateTime));
                } else {
                    samples.add(item.name);
                }
            }
        }
        return TextUtils.join("\n", samples);
    }

    private String joinLowStockSamples(Context context, List<Item> items) {
        List<String> samples = new ArrayList<>();
        if (items != null) {
            for (Item item : items) {
                if (item.minAmount != null) {
                    samples.add(context.getString(R.string.dashboard_low_stock_item_,
                            item.name, item.minAmount));
                } else {
                    samples.add(item.name);
                }
            }
        }
        return TextUtils.join("\n", samples);
    }

    private String joinReminderSamples(List<ItemReminder> itemReminders) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM", mLocale);
        List<String> samples = new ArrayList<>();
        if (itemReminders != null) {
            for (ItemReminder itemReminder : itemReminders) {
                if (itemReminder.reminderDateTime != null) {
                    samples.add(itemReminder.message + " — "
                            + dateFormat.format(itemReminder.reminderDateTime));
                } else {
                    samples.add(itemReminder.message);
                }
            }
        }
        return TextUtils.join("\n", samples);
    }

    private void doExport(Activity activity) {
        setLoading(true);
        ProgressSVDialog.Args args = ProgressSVDialog.Args.newArgs(
                activity.getString(R.string.export_data),
                activity.getString(R.string.export_progress_gathering));
        mNavigator.push(Routes.COMMON_PROGRESS_DIALOG, args);
        mRxDisposer.add("home_export_progress",
                mExportCmd.getProgressFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(args.getProgressSubject()::onNext, throwable -> {
                        })
        );
        mRxDisposer.add("home_export_execute",
                mExportCmd.execute()
                        .observeOn(AndroidSchedulers.mainThread())
                        .doFinally(() -> mNavigator.pop())
                        .subscribe(
                                zipFile -> {
                                    setLoading(false);
                                    UiUtils.shareFile(activity, zipFile,
                                            activity.getString(R.string.share_backup_file));
                                },
                                throwable -> {
                                    setLoading(false);
                                    mLogger.e(TAG, activity.getString(R.string.export_failed), throwable);
                                }
                        )
        );
    }

    private void showExportingState() {
        setLoading(true);
    }

    private void doExportSpreadsheet(Activity activity) {
        showExportingState();
        ProgressSVDialog.Args args = ProgressSVDialog.Args.newArgs(
                activity.getString(R.string.export_spreadsheet),
                activity.getString(R.string.export_spreadsheet_progress_writing));
        mNavigator.push(Routes.COMMON_PROGRESS_DIALOG, args);
        // keyed RxDisposer subscriptions: a re-attach replaces the previous
        // subscription per key instead of stacking duplicates
        mRxDisposer.add("home_export_spreadsheet_progress",
                mExportSpreadsheetCmd.getProgressFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(args.getProgressSubject()::onNext, throwable -> {
                        })
        );
        mRxDisposer.add("home_export_spreadsheet_execute",
                mExportSpreadsheetCmd.execute()
                        .observeOn(AndroidSchedulers.mainThread())
                        .doFinally(() -> mNavigator.pop())
                        .subscribe(
                                file -> {
                                    setLoading(false);
                                    UiUtils.shareFile(activity, file,
                                            activity.getString(R.string.share_spreadsheet_file),
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                                },
                                throwable -> {
                                    setLoading(false);
                                    mLogger.e(TAG, activity.getString(R.string.export_spreadsheet_failed), throwable);
                                }
                        )
        );
    }

    private void doImport(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        activity.startActivityForResult(intent, REQUEST_CODE_IMPORT);
    }

    private void performImport(Activity activity, Uri uri) {
        setLoading(true);
        ProgressSVDialog.Args args = ProgressSVDialog.Args.newArgs(
                activity.getString(R.string.import_data),
                activity.getString(R.string.import_progress_extracting));
        mNavigator.push(Routes.COMMON_PROGRESS_DIALOG, args);
        mRxDisposer.add("home_import_progress",
                mImportCmd.getProgressFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(args.getProgressSubject()::onNext, throwable -> {
                        })
        );
        mRxDisposer.add("home_import_execute",
                Single.fromCallable(() -> mFileHelper.createTempFile("backup_import.aps_backup", uri))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .flatMap(tempFile -> mImportCmd.execute(tempFile))
                        .observeOn(AndroidSchedulers.mainThread())
                        .doFinally(() -> mNavigator.pop())
                        .subscribe(
                                count -> {
                                    setLoading(false);
                                    mLogger.i(TAG, activity.getString(R.string.import_success, count));
                                },
                                throwable -> {
                                    setLoading(false);
                                    mLogger.e(TAG, activity.getString(R.string.import_failed), throwable);
                                }
                        )
        );
    }

    private void setLoading(boolean loading) {
        if (mButtonExport != null) {
            mButtonExport.setEnabled(!loading);
        }
        if (mButtonImport != null) {
            mButtonImport.setEnabled(!loading);
        }
        if (mButtonExportSpreadsheet != null) {
            mButtonExportSpreadsheet.setEnabled(!loading);
        }
    }

    private void itemSelectedForReminder(NavRoute navRoute) {
        ItemSelectPage.Result result = ItemSelectPage.Result.of(navRoute);
        if (result != null) {
            ItemState itemState = result.getItemState();
            mNavigator.push(Routes.ITEM_REMINDER_DETAIL_PAGE,
                    ItemReminderDetailPage.Args.with(itemState.getItemId()));
        }
    }

    private void itemSelectedForUsage(NavRoute navRoute) {
        ItemSelectPage.Result result = ItemSelectPage.Result.of(navRoute);
        if (result != null) {
            ItemState itemState = result.getItemState();
            mNavigator.push(Routes.ITEM_USAGE_DETAIL_PAGE,
                    ItemUsageDetailPage.Args.with(itemState.getItemId()));
        }
    }

    private void itemSelectedForPurchase(NavRoute navRoute) {
        ItemSelectPage.Result result = ItemSelectPage.Result.of(navRoute);
        if (result != null) {
            ItemState itemState = result.getItemState();
            mNavigator.push(Routes.ITEM_PURCHASE_DETAIL_PAGE,
                    ItemPurchaseDetailPage.Args.with(itemState.getItemId()));
        }
    }

    private void itemSelectedForMaintenance(NavRoute navRoute) {
        ItemSelectPage.Result result = ItemSelectPage.Result.of(navRoute);
        if (result != null) {
            ItemState itemState = result.getItemState();
            mNavigator.push(Routes.ITEM_MAINTENANCE_DETAIL_PAGE,
                    ItemMaintenanceDetailPage.Args.with(itemState.getItemId()));
        }
    }
}
