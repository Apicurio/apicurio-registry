package io.apicurio.registry.metrics.health;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Abstract class containing common logic for health checks based on an error counter.
 *
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public abstract class AbstractErrorCounterHealthCheck {

    protected long errorCounter = 0;
    private Instant nextCounterReset;
    private Optional<Duration> statusResetWindowDuration;
    private Optional<Instant> nextStatusReset;
    protected boolean up = true;
    private Duration counterResetWindowDuration;
    private Integer configErrorThreshold;

    protected void init(Integer configErrorThreshold, Integer configCounterResetWindowDurationSec, Integer configStatusResetWindowDurationSec) {
        if (configErrorThreshold == null || configErrorThreshold < 0) {
            throw new IllegalArgumentException("Illegal configuration value of " +
                    "'registry.metrics.[...].errorThreshold': '" + configErrorThreshold + "'");
        }
        this.configErrorThreshold = configErrorThreshold;
        if (configCounterResetWindowDurationSec == null || configCounterResetWindowDurationSec < 1) {
            throw new IllegalArgumentException("Illegal configuration value of " +
                    "'registry.metrics.[...].counterResetWindowDurationSec': '" + configCounterResetWindowDurationSec + "'");
        }
        if (configStatusResetWindowDurationSec == null) {
            throw new IllegalArgumentException("Illegal configuration value of " +
                    "'registry.metrics.[...].statusResetWindowDurationSec': '" + configCounterResetWindowDurationSec + "'");
        }
        counterResetWindowDuration = Duration.ofSeconds(configCounterResetWindowDurationSec);
        nextCounterReset = Instant.now().plus(counterResetWindowDuration);
        if (configStatusResetWindowDurationSec > 0) {
            statusResetWindowDuration = Optional.of(Duration.ofSeconds(configStatusResetWindowDurationSec));
        }
    }

    protected synchronized void suspectSuper() {
        nextCounterReset = Instant.now().plus(counterResetWindowDuration);
        if (++errorCounter > configErrorThreshold) {
            up = false;
            statusResetWindowDuration.ifPresent(duration -> nextStatusReset = Optional.of(Instant.now().plus(duration)));
        }
    }

    protected synchronized void callSuper() {
        if (!up && nextStatusReset.isPresent() && Instant.now().isAfter(nextStatusReset.get())) {
            nextStatusReset = Optional.empty();
            up = true; // Next 'if' will reset the error count
        }
        if (up && nextCounterReset != null && Instant.now().isAfter(nextCounterReset)) { // Do not reset the count if not up
            nextCounterReset = null;
            errorCounter = 0;
        }
    }
}
