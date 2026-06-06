package m.co.rh.id.a_personal_stuff.settings.ui.page;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.base.provider.FileHelper;
import m.co.rh.id.a_personal_stuff.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_personal_stuff.base.rx.RxDisposer;
import m.co.rh.id.a_personal_stuff.base.ui.component.AppBarSV;
import m.co.rh.id.a_personal_stuff.base.util.UiUtils;
import m.co.rh.id.a_personal_stuff.settings.R;
import m.co.rh.id.a_personal_stuff.settings.ui.component.LogLineAdapter;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class LogPage extends StatefulView<Activity> implements RequireComponent<Provider> {
    private static final String TAG = LogPage.class.getName();

    @NavInject
    private AppBarSV mAppBarSV;

    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient Handler mHandler;
    private transient RxDisposer mRxDisposer;
    private transient FileHelper mFileHelper;

    private SerialBehaviorSubject<File> mLogFile;

    public LogPage() {
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mLogger = mSvProvider.get(ILogger.class);
        mHandler = mSvProvider.get(Handler.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mFileHelper = mSvProvider.get(FileHelper.class);
        if (mLogFile == null) {
            mLogFile = new SerialBehaviorSubject<>(mFileHelper.getLogFile());
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.page_log,
                container, false);
        ViewGroup rootLayout = view.findViewById(R.id.root_layout);
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.log_file));
        containerAppBar.addView(mAppBarSV.buildView(activity, rootLayout));
        ProgressBar progressBar = view.findViewById(R.id.progress_circular);
        View noRecord = view.findViewById(R.id.no_record);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        LogLineAdapter adapter = new LogLineAdapter();
        recyclerView.setAdapter(adapter);
        FloatingActionButton fabClear = view.findViewById(R.id.fab_clear);
        FloatingActionButton fabShare = view.findViewById(R.id.fab_share);
        fabShare.setOnClickListener(v -> {
            try {
                UiUtils.shareFile(activity, mLogFile.getValue(), activity.getString(R.string.share_log_file));
            } catch (Throwable e) {
                mSvProvider.get(ILogger.class)
                        .e(TAG, activity.getString(R.string.error_sharing_log_file), e);
            }
        });
        fabClear.setOnClickListener(view1 -> {
            mFileHelper.clearLogFile();
            mLogger.i(TAG, activity.getString(R.string.log_file_deleted));
            mHandler
                    .post(() -> mLogFile.onNext(mLogFile.getValue()));
        });
        mRxDisposer.add("createView_readLogFile",
                mLogFile.getSubject().
                        observeOn(Schedulers.from(mExecutorService))
                        .subscribe(file -> {
                            List<String> logLines = new ArrayList<>();
                            if (file.exists()) {
                                try (BufferedReader bufferedReader =
                                             new BufferedReader(new FileReader(file))) {
                                    String line;
                                    while ((line = bufferedReader.readLine()) != null) {
                                        logLines.add(line);
                                    }
                                }
                            }
                            mHandler.post(() -> {
                                progressBar.setVisibility(View.GONE);
                                adapter.setLogLines(logLines);
                                if (logLines.isEmpty()) {
                                    noRecord.setVisibility(View.VISIBLE);
                                    recyclerView.setVisibility(View.GONE);
                                    fabShare.setVisibility(View.GONE);
                                    fabClear.setVisibility(View.GONE);
                                } else {
                                    noRecord.setVisibility(View.GONE);
                                    recyclerView.setVisibility(View.VISIBLE);
                                    recyclerView.post(() ->
                                            recyclerView.scrollToPosition(
                                                    adapter.getItemCount() - 1));
                                    fabShare.setVisibility(View.VISIBLE);
                                    fabClear.setVisibility(View.VISIBLE);
                                }
                            });
                        })
        );


        return view;
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
    }
}
