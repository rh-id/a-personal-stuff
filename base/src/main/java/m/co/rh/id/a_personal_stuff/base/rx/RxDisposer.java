package m.co.rh.id.a_personal_stuff.base.rx;

import android.content.Context;

import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.rxjava3.disposables.Disposable;
import m.co.rh.id.aprovider.ProviderDisposable;

/**
 * Helper class to help manage Rx disposable instances
 */
public class RxDisposer implements ProviderDisposable {
    private ConcurrentHashMap<String, Disposable> disposableMap;

    public RxDisposer() {
        disposableMap = new ConcurrentHashMap<>();
    }

    public void add(String uniqueKey, Disposable disposable) {
        disposableMap.compute(uniqueKey, (key, existing) -> {
            if (existing != null) {
                existing.dispose();
            }
            return disposable;
        });
    }

    public void dispose() {
        for (ConcurrentHashMap.Entry<String, Disposable> entry : disposableMap.entrySet()) {
            entry.getValue().dispose();
        }
        disposableMap.clear();
    }

    @Override
    public void dispose(Context context) {
        dispose();
    }
}
