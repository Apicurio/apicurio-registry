package io.apicurio.registry.metrics;

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

/**
 * Fail liveness check if the number of exceptions thrown by artifactStore is too high.
 *
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Interceptor
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
