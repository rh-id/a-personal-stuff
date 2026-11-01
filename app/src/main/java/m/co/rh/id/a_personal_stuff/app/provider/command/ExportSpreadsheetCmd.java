package m.co.rh.id.a_personal_stuff.app.provider.command;

import android.os.Build;

import java.io.File;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.app.provider.service.ExportService;
import m.co.rh.id.apoi_spreadsheet.base.POISpreadsheetContext;
import m.co.rh.id.aprovider.Provider;

/**
 * Thin Rx adapter over the stateless {@link ExportService}, adding progress
 * reporting for the multi-sheet XLSX spreadsheet export. Requires API 26 or
 * higher (see {@link #isSupported()}).
 */
public class ExportSpreadsheetCmd {

    private final Subject<String> mProgressSubject = PublishSubject.create();
    // POI writes the workbook on its dedicated single-thread executor;
    // non-interruptible so a disposed subscription cannot interrupt a
    // workbook mid-write and leave a stale interrupt flag poisoning
    // subsequent exports (disposal just drops the result).
    private final Scheduler mSpreadsheetScheduler =
            Schedulers.from(POISpreadsheetContext.getInstance(), false);
    private final ExportService mExportService;

    public ExportSpreadsheetCmd(Provider provider) {
        mExportService = provider.get(ExportService.class);
    }

    public Flowable<String> getProgressFlow() {
        return Flowable.fromObservable(mProgressSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * @return true when this device can run the XLSX export (API 26+).
     */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public Single<File> execute() {
        return Single.fromCallable(() -> mExportService.createSpreadsheetXlsx(mProgressSubject::onNext))
                .subscribeOn(mSpreadsheetScheduler);
    }
}
