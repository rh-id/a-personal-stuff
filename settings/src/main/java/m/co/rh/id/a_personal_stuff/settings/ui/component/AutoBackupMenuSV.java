package m.co.rh.id.a_personal_stuff.settings.ui.component;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.core.os.ConfigurationCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.settings.R;
import m.co.rh.id.a_personal_stuff.settings.provider.component.SettingsSharedPreferences;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class AutoBackupMenuSV extends StatefulView<Activity> implements RequireComponent<Provider>,
        CompoundButton.OnCheckedChangeListener,
        View.OnClickListener, NavOnActivityResult<Activity> {

    private static final String TAG = AutoBackupMenuSV.class.getName();

    // 1001 (HomePage import) and 2 (AlertSettingsMenuSV notification
    // permission) are already taken by other request codes
    private static final int REQUEST_CODE_AUTO_BACKUP_FOLDER = 1002;

    private transient Provider mSvProvider;
    private transient SettingsSharedPreferences mSettingsSharedPreferences;
    private transient RxDisposer mRxDisposer;
    private transient ILogger mLogger;
    private transient ExecutorService mExecutorService;
    private transient Activity mActivity;
    private transient Locale mLocale;
    private transient TextInputLayout mIntervalInputLayout;
    private transient TextInputEditText mIntervalInput;
    private transient TextView mTextAutoBackupFolder;
    private transient TextView mTextAutoBackupStatus;
    private transient View mButtonChooseFolder;

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mSettingsSharedPreferences = mSvProvider.get(SettingsSharedPreferences.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mLogger = mSvProvider.get(ILogger.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        mActivity = activity;
        mLocale = ConfigurationCompat.getLocales(activity.getResources().getConfiguration()).get(0);
        View view = activity.getLayoutInflater().inflate(R.layout.menu_auto_backup, container, false);
        CompoundButton switchAutoBackup = view.findViewById(R.id.switch_auto_backup);
        TextInputLayout intervalInputLayout = view.findViewById(R.id.form_auto_backup_interval);
        TextInputEditText intervalInput = view.findViewById(R.id.edit_text_auto_backup_interval);
        TextView textAutoBackupFolder = view.findViewById(R.id.text_auto_backup_folder);
        TextView textAutoBackupStatus = view.findViewById(R.id.text_auto_backup_status);
        View buttonChooseFolder = view.findViewById(R.id.button_choose_folder);
        // assign before use so updateEnabledState/refreshFolderName can reach them
        mIntervalInputLayout = intervalInputLayout;
        mIntervalInput = intervalInput;
        mTextAutoBackupFolder = textAutoBackupFolder;
        mTextAutoBackupStatus = textAutoBackupStatus;
        mButtonChooseFolder = buttonChooseFolder;
        // apply current values before attaching listeners so initialization
        // does not write back to the shared preferences
        switchAutoBackup.setChecked(mSettingsSharedPreferences.isAutoBackupEnabled());
        intervalInput.setText(String.valueOf(mSettingsSharedPreferences.getAutoBackupIntervalDays()));
        updateEnabledState(mSettingsSharedPreferences.isAutoBackupEnabled());
        refreshFolderName();
        updateStatusText();
        // re-render the status line whenever the worker records a new success
        // timestamp or failure message; the BehaviorSubjects replay their
        // current values, so the initial render is covered by this too
        mRxDisposer.add("createView_onAutoBackupStatusChanged",
                Flowable.combineLatest(
                        mSettingsSharedPreferences.getAutoBackupLastSuccessDateTimeFlow(),
                        mSettingsSharedPreferences.getAutoBackupLastFailureFlow(),
                        (lastSuccess, lastFailure) -> true)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> updateStatusText()));
        switchAutoBackup.setOnCheckedChangeListener(this);
        intervalInput.addTextChangedListener(new TextWatcher() {
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
                applyIntervalText(editable.toString(), false);
            }
        });
        // focus-out safety net: on focus loss an empty/unparsable/out-of-range
        // text is replaced with the interval value that is actually in effect
        intervalInput.setOnFocusChangeListener((focusedView, hasFocus) -> {
            if (!hasFocus) {
                applyIntervalText(getIntervalInputText(), true);
            }
        });
        buttonChooseFolder.setOnClickListener(this);
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mActivity = null;
        mIntervalInputLayout = null;
        mIntervalInput = null;
        mTextAutoBackupFolder = null;
        mTextAutoBackupStatus = null;
        mButtonChooseFolder = null;
        mLogger = null;
        if (mSvProvider != null) {
            // also disposes this view's RxDisposer, cancelling any in-flight
            // folder name lookup
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        int id = compoundButton.getId();
        if (id == R.id.switch_auto_backup) {
            mSettingsSharedPreferences.setAutoBackupEnabled(isChecked);
            updateEnabledState(isChecked);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_choose_folder) {
            Activity activity = mActivity;
            if (activity != null) {
                activity.startActivityForResult(
                        new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                        REQUEST_CODE_AUTO_BACKUP_FOLDER);
            }
        }
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator navigator, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_AUTO_BACKUP_FOLDER
                && resultCode == Activity.RESULT_OK
                && data != null
                && data.getData() != null) {
            Uri treeUri = data.getData();
            try {
                // persist the grant so the worker can still read/write the
                // folder after device restarts
                activity.getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Throwable throwable) {
                ILogger logger = mLogger;
                if (logger != null) {
                    logger.e(TAG, throwable.getMessage(), throwable);
                }
                return;
            }
            mSettingsSharedPreferences.setAutoBackupTreeUri(treeUri.toString());
            refreshFolderName();
        }
    }

    // ViewGroup.setEnabled does not propagate to children, so each child view
    // (input, text, button) must be disabled individually for the state to be
    // visible
    private void updateEnabledState(boolean enabled) {
        if (mIntervalInputLayout != null) {
            mIntervalInputLayout.setEnabled(enabled);
        }
        if (mIntervalInput != null) {
            mIntervalInput.setEnabled(enabled);
        }
        if (mTextAutoBackupFolder != null) {
            mTextAutoBackupFolder.setEnabled(enabled);
        }
        if (mTextAutoBackupStatus != null) {
            mTextAutoBackupStatus.setEnabled(enabled);
        }
        if (mButtonChooseFolder != null) {
            mButtonChooseFolder.setEnabled(enabled);
        }
    }

    private void refreshFolderName() {
        Activity activity = mActivity;
        TextView textFolder = mTextAutoBackupFolder;
        if (activity == null || textFolder == null) {
            return;
        }
        String treeUriString = mSettingsSharedPreferences.getAutoBackupTreeUri();
        if (treeUriString.isEmpty()) {
            textFolder.setText(R.string.auto_backup_folder_none);
            return;
        }
        Uri treeUri = Uri.parse(treeUriString);
        // resolve the display name off the main thread; the RxDisposer is
        // disposed together with this view, guarding late result delivery
        mRxDisposer.add("createView_refreshFolderName",
                Single.fromCallable(() -> {
                    DocumentFile tree = DocumentFile.fromTreeUri(
                            activity.getApplicationContext(), treeUri);
                    String name = tree != null ? tree.getName() : null;
                    if (name == null || name.isEmpty()) {
                        return activity.getString(R.string.auto_backup_folder_set);
                    }
                    return name;
                })
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(folderName -> {
                            TextView textView = mTextAutoBackupFolder;
                            if (textView != null) {
                                textView.setText(folderName);
                            }
                        }, throwable -> {
                            ILogger logger = mLogger;
                            if (logger != null) {
                                logger.e(TAG, throwable.getMessage(), throwable);
                            }
                        }));
    }

    private void updateStatusText() {
        Activity activity = mActivity;
        TextView textStatus = mTextAutoBackupStatus;
        if (activity == null || textStatus == null) {
            return;
        }
        long lastSuccess = mSettingsSharedPreferences.getAutoBackupLastSuccessDateTime();
        String lastFailure = mSettingsSharedPreferences.getAutoBackupLastFailure();
        String status;
        if (lastSuccess > 0) {
            String formatted = new SimpleDateFormat("dd MMM yyyy HH:mm", mLocale)
                    .format(new Date(lastSuccess));
            status = activity.getString(R.string.auto_backup_last_success, formatted);
        } else if (!lastFailure.isEmpty()) {
            status = activity.getString(R.string.auto_backup_last_failure, lastFailure);
        } else {
            status = "";
        }
        textStatus.setText(status);
    }

    private void applyIntervalText(String text, boolean forceResetOnInvalid) {
        SettingsSharedPreferences settings = mSettingsSharedPreferences;
        TextInputEditText intervalInput = mIntervalInput;
        if (settings == null || intervalInput == null) {
            return;
        }
        int stored = settings.getAutoBackupIntervalDays();
        Integer parsed = parseIntervalDays(text);
        if (parsed != null) {
            // skip redundant writes to avoid pointless scheduler churn
            if (parsed != stored) {
                settings.setAutoBackupIntervalDays(parsed);
            }
        } else if (forceResetOnInvalid) {
            intervalInput.setText(String.valueOf(stored));
        }
    }

    private String getIntervalInputText() {
        TextInputEditText intervalInput = mIntervalInput;
        if (intervalInput == null) {
            return "";
        }
        Editable text = intervalInput.getText();
        return text != null ? text.toString() : "";
    }

    /**
     * Parses a backup interval in days; returns null (invalid) when the text
     * is empty, unparsable, or outside 1..365.
     */
    private static Integer parseIntervalDays(String text) {
        String trimmed = text != null ? text.trim() : "";
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(trimmed);
            if (value < 1 || value > 365) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
