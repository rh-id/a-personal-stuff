package m.co.rh.id.a_personal_stuff.app.ui.page;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.R;
import m.co.rh.id.a_personal_stuff.app.provider.command.QueryItemCmd;
import m.co.rh.id.a_personal_stuff.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_personal_stuff.base.constants.Routes;
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
        ViewGroup containerAppBar = rootLayout.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
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
        }
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
