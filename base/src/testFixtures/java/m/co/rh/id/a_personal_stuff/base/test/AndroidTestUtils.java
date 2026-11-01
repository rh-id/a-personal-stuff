package m.co.rh.id.a_personal_stuff.base.test;

/**
 * Shared androidTest helpers. Replaces the per-file duplicated
 * {@code await(...)} polling helpers.
 */
public class AndroidTestUtils {

    private static final long TIMEOUT_MILLIS = 5000;
    private static final long POLL_INTERVAL_MILLIS = 50;

    private AndroidTestUtils() {
    }

    /**
     * The condition passed to {@link #await(Condition)}. Deliberately NOT
     * {@code java.util.function.BooleanSupplier}: its {@code getAsBoolean}
     * is API 24+ while this module's minSdk is 21, and lint flags the call
     * as NewApi on the testFixtures/androidTest source sets — desugaring
     * notwithstanding. The custom SAM type is lint-clean, and call sites
     * are unchanged — lambdas and method references target this interface
     * the same way.
     */
    public interface Condition {
        boolean getAsBoolean();
    }

    /**
     * Polls until the condition holds or the timeout elapses.
     */
    public static boolean await(Condition condition) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline && !condition.getAsBoolean()) {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
        return condition.getAsBoolean();
    }
}
