package io.apicurio.registry.metrics;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.RegistryExceptionMapper;

/**
 * Fail liveness check if the number of exceptions thrown by storage is too high.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Interceptor
@PersistenceExceptionLivenessApply
public class PersistenceExceptionLivenessInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(PersistenceExceptionLivenessInterceptor.class);

    private static final Set<String> IGNORED_CLASSES = new HashSet<>();
    static {
        IGNORED_CLASSES.add("io.grpc.StatusRuntimeException");
        IGNORED_CLASSES.add("org.apache.kafka.streams.errors.InvalidStateStoreException");
    }

    @Inject
    PersistenceExceptionLivenessCheck check;

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        try {
            return context.proceed();
        } catch (Exception ex) {
            if (!this.isIgnoreError(ex)) {
                check.suspectWithException(ex);
            } else {
                log.debug("Ignored intercepted exception: " + ex.getClass().getName() + " :: " + ex.getMessage());
            }
            throw ex;
        }
    }

    private boolean isIgnoreError(Exception ex) {
        if (ex instanceof LivenessIgnoredException) {
            return true;
        }
        Set<Class<? extends Exception>> ignored = RegistryExceptionMapper.getIgnored();
        return ignored.contains(ex.getClass());
    }

}
