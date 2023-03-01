package io.apicurio.registry.metrics.health.readiness;

import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import jakarta.annotation.Priority;
import org.slf4j.Logger;

/**
 * Fail readiness check if the duration of processing a artifactStore operation is too high.
 *
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
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
