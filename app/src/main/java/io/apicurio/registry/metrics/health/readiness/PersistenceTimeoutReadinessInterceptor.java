package io.apicurio.registry.metrics.health.readiness;

import io.apicurio.registry.util.Priorities;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;

import java.time.Instant;

/**
 * Fail readiness check if the duration of processing a artifactStore operation is too high.
 */
@Interceptor
@Priority(Priorities.Interceptors.APPLICATION)
@PersistenceTimeoutReadinessApply
public class PersistenceTimeoutReadinessInterceptor {

    @Inject
    Logger log;

    @Inject
    PersistenceTimeoutReadinessCheck check;

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        Instant start = Instant.now();
        Object result = context.proceed();
        if (start.plus(check.getTimeoutSec()).isBefore(Instant.now())) {
            check.suspect();
        }
        return result;
    }
}
