package io.apicurio.registry.metrics;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.time.Duration;
import java.time.Instant;

/**
 * Fail readiness check if the duration of processing a storage operation is too high.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Interceptor
@PersistenceTimeoutReadinessApply
public class PersistenceTimeoutReadinessInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PersistenceTimeoutReadinessInterceptor.class);

    @Inject
    PersistenceTimeoutReadinessCheck check;

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        Instant start = Instant.now();
        Object result = context.proceed();
        if (start.plus(check.getTimeoutSec()).isBefore(Instant.now())) {
            check.suspectSuper();
        }
        return result;
    }
}
