package io.apicurio.registry.metrics.health.readiness;

import java.time.Instant;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.slf4j.Logger;

/**
 * Fail readiness check if the duration of processing a artifactStore operation is too high.
 *
 * @author Jakub Senko 'jsenko@redhat.com'
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
