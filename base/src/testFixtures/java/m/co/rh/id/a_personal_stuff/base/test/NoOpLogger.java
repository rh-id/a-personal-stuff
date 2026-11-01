package m.co.rh.id.a_personal_stuff.base.test;

import m.co.rh.id.alogger.ILogger;

/**
 * Shared androidTest no-op {@link ILogger}: every method swallows the log
 * line so tests can register a logger without touching logcat. One copy
 * lives here; the former per-file anonymous/named implementations are all
 * replaced by this class.
 */
public class NoOpLogger implements ILogger {
    @Override
    public void v(String tag, String message) {
    }

    @Override
    public void v(String tag, String message, Throwable throwable) {
    }

    @Override
    public void d(String tag, String message) {
    }

    @Override
    public void d(String tag, String message, Throwable throwable) {
    }

    @Override
    public void i(String tag, String message) {
    }

    @Override
    public void i(String tag, String message, Throwable throwable) {
    }

    @Override
    public void w(String tag, String message) {
    }

    @Override
    public void w(String tag, String message, Throwable throwable) {
    }

    @Override
    public void e(String tag, String message) {
    }

    @Override
    public void e(String tag, String message, Throwable throwable) {
    }

    @Override
    public void setLogLevel(int logLevel) {
    }
}
