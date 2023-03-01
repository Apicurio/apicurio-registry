package io.apicurio.registry.metrics.health.liveness;

import jakarta.annotation.Priority;

import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Fail liveness check if the number of exceptions thrown by artifactStore is too high.
 *
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
@PersistenceExceptionLivenessApply
public class PersistenceExceptionLivenessInterceptor {

    @Inject
    PersistenceExceptionLivenessCheck check;
    @Inject
    LivenessUtil livenessUtil;

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        try {
            return context.proceed();
        } catch (Exception ex) {
            if (!livenessUtil.isIgnoreError(ex)) {
                check.suspectWithException(ex);
            }
            throw ex;
        }
    }

}
