package m.co.rh.id.a_personal_stuff.base.test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs every task inline on the calling thread so Flowable/subject
 * emissions and blockingGet chains are deterministic without latches
 * (a pooled executor makes assertions racy). Shared androidTest fixture —
 * register it under {@code ExecutorService.class} in test provider modules.
 * <p>
 * Lifecycle is spec-shaped so it stays a faithful stand-in for the
 * production {@code WeightedThreadPool}: {@link #shutdown()} and
 * {@link #shutdownNow()} only flip a flag (tasks run on the CALLER's
 * thread, so there are no worker threads of our own to interrupt and
 * nothing queued to drain — {@link #shutdownNow()} therefore never
 * interrupts and always returns an empty list), {@link #execute(Runnable)}
 * rejects with {@link RejectedExecutionException} after shutdown per the
 * ExecutorService contract, and {@link #awaitTermination(long, TimeUnit)}
 * reports when in-flight (still-running caller-thread) tasks have finished.
 */
public class DirectExecutorService extends AbstractExecutorService {

    private static final long TERMINATION_POLL_INTERVAL_MILLIS = 50;

    private volatile boolean mShutdown;
    private final AtomicInteger mActiveTasks = new AtomicInteger();

    @Override
    public void execute(Runnable command) {
        if (mShutdown) {
            throw new RejectedExecutionException(
                    "DirectExecutorService is shut down");
        }
        mActiveTasks.incrementAndGet();
        try {
            command.run();
        } finally {
            mActiveTasks.decrementAndGet();
        }
    }

    @Override
    public void shutdown() {
        mShutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        mShutdown = true;
        // tasks run synchronously on the caller's thread: nothing is queued
        // on our side to drain, so no running task is interrupted and the
        // (always empty) queue is returned
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return mShutdown;
    }

    @Override
    public boolean isTerminated() {
        return mShutdown && mActiveTasks.get() == 0;
    }

    /**
     * Since every task runs on the caller's thread, termination can only be
     * reached when no nested task is mid-run; the timeout is waited out with
     * a simple poll loop (same shape as {@code AndroidTestUtils.await}) —
     * there is no internal thread whose join could replace it.
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (!isTerminated()) {
            if (System.nanoTime() >= deadlineNanos) {
                return false;
            }
            Thread.sleep(TERMINATION_POLL_INTERVAL_MILLIS);
        }
        return true;
    }
}
