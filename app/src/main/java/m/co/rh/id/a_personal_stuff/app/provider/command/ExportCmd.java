package m.co.rh.id.a_personal_stuff.app.provider.command;

import java.io.File;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import m.co.rh.id.a_personal_stuff.app.provider.service.ExportService;
import m.co.rh.id.aprovider.Provider;

/**
 * Thin Rx adapter over the stateless {@link ExportService}, adding progress reporting.
 */
public class ExportCmd {

    private final ExecutorService mExecutorService;
    private final Subject<String> mProgressSubject = PublishSubject.create();
    private final ExportService mExportService;

    public ExportCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mExportService = provider.get(ExportService.class);
    }

    public Flowable<String> getProgressFlow() {
        return Flowable.fromObservable(mProgressSubject, BackpressureStrategy.BUFFER);
    }

    public Single<File> execute() {
        return Single.fromCallable(() -> mExportService.createBackupZip(mProgressSubject::onNext))
                .subscribeOn(Schedulers.from(mExecutorService));
    }
}
