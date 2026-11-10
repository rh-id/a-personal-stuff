package m.co.rh.id.a_personal_stuff.app.provider.command;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_personal_stuff.app.provider.service.StatisticsService;
import m.co.rh.id.a_personal_stuff.app.ui.model.StatisticsData;
import m.co.rh.id.aprovider.Provider;

/**
 * Thin Rx adapter over the stateless {@link StatisticsService}: the blocking
 * statistics aggregation runs on the shared weight-5 executor pool.
 */
public class StatisticsCmd {

    private final ExecutorService mExecutorService;
    private final StatisticsService mStatisticsService;

    public StatisticsCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mStatisticsService = provider.get(StatisticsService.class);
    }

    public Single<StatisticsData> execute() {
        return Single.fromCallable(() -> mStatisticsService.computeStatistics())
                .subscribeOn(Schedulers.from(mExecutorService));
    }
}
