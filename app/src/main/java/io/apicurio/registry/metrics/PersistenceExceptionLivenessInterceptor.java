package io.apicurio.registry.metrics;

import io.apicurio.registry.rest.RegistryExceptionMapper;

import java.util.Set;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

/**
 * Fail liveness check if the number of exceptions thrown by storage is too high.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Interceptor
@PersistenceExceptionLivenessApply
public class PersistenceExceptionLivenessInterceptor {

    @Inject
    PersistenceExceptionLivenessCheck check;

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        try {
            return context.proceed();
        } catch (Exception ex) {
            Set<Class<? extends Exception>> ignored = RegistryExceptionMapper.getIgnored();
            if (!ignored.contains(ex.getClass())) { // suspect and rethrow unless ignored
                check.suspectWithException(ex);
            }
            throw ex;
        }
    }

}
