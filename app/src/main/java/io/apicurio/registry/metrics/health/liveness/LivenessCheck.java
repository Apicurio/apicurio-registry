package io.apicurio.registry.metrics.health.liveness;

/**
 * Common interface for a liveness check.
 *
 */
public interface LivenessCheck {

    void suspect(String reason);

    void suspectWithException(Throwable reason);
}
