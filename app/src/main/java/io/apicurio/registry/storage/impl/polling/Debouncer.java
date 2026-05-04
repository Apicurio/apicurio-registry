package io.apicurio.registry.storage.impl.polling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

import static io.apicurio.registry.utils.TimeUtils.isPositive;

/**
 * Debounces rapid successive changes by waiting for a quiet period before signaling readiness.
 * <p>
 * When a change is detected, the debouncer starts tracking. It signals ready to process when
 * either the quiet period elapses (no new changes) or the max wait time is exceeded (prevents
 * indefinite delay under continuous changes).
 * <p>
 * This class is not thread-safe — callers must ensure external synchronization if needed.
 *
 * @param <T> the type of value being debounced (e.g., a poll result)
 */
public class Debouncer<T> {

    private static final Logger log = LoggerFactory.getLogger(Debouncer.class);

    private final Duration quietPeriod;
    private final Duration maxWait;

    private Instant firstChangeAt;
    private Instant lastChangeAt;
    private T pending;

    public Debouncer(Duration quietPeriod, Duration maxWait) {
        this.quietPeriod = quietPeriod;
        this.maxWait = maxWait;
    }

    /**
     * Records a change with the given value. If this is the first change in a debounce cycle,
     * starts tracking. Subsequent changes reset the quiet period timer.
     */
    public void onChange(T value) {
        Instant now = Instant.now();
        if (firstChangeAt == null) {
            firstChangeAt = now;
            log.info("Change detected, starting debounce (quiet={}, max={})", quietPeriod, maxWait);
        }
        lastChangeAt = now;
        pending = value;
    }

    /**
     * Returns whether there is a pending change that is ready to be processed.
     * Ready when:
     * <ul>
     * <li>Debouncing is disabled (quiet period is zero) and there is a pending change</li>
     * <li>The quiet period has elapsed since the last change</li>
     * <li>The max wait time has been exceeded since the first change</li>
     * </ul>
     */
    public boolean isReady() {
        if (pending == null) {
            return false;
        }

        if (!isPositive(quietPeriod)) {
            return true;
        }

        Instant now = Instant.now();
        Duration sinceFirst = Duration.between(firstChangeAt, now);
        Duration sinceLast = Duration.between(lastChangeAt, now);

        if (isPositive(maxWait) && sinceFirst.compareTo(maxWait) >= 0) {
            log.info("Debounce max wait exceeded ({}), forcing processing", sinceFirst);
            return true;
        }

        if (sinceLast.compareTo(quietPeriod) >= 0) {
            log.info("Debounce quiet period elapsed ({} since last change)", sinceLast);
            return true;
        }

        log.debug("Debouncing: waiting for quiet period ({} since last change, {} since first change)",
                sinceLast, sinceFirst);
        return false;
    }

    /**
     * Returns the pending value, or null if no change is pending.
     */
    public T pending() {
        return pending;
    }

    /**
     * Resets the debouncer state. Call after the pending change has been processed.
     */
    public void reset() {
        firstChangeAt = null;
        lastChangeAt = null;
        pending = null;
    }
}
