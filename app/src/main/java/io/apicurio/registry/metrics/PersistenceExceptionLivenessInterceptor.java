package io.apicurio.registry.metrics;

import io.apicurio.registry.storage.AlreadyExistsException;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.NotFoundException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.VersionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.util.HashSet;
import java.util.Set;

/**
 * Fail liveness check if the number of exceptions thrown by storage is too high.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Interceptor
@PersistenceExceptionLivenessApply
public class PersistenceExceptionLivenessInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PersistenceExceptionLivenessInterceptor.class);

    private static final Set<Class<? extends Exception>> IGNORED = new HashSet<>();

    static {
        IGNORED.add(AlreadyExistsException.class);
        IGNORED.add(ArtifactAlreadyExistsException.class);
        IGNORED.add(ArtifactNotFoundException.class);
        IGNORED.add(NotFoundException.class);
        IGNORED.add(RuleAlreadyExistsException.class);
        IGNORED.add(RuleNotFoundException.class);
        IGNORED.add(VersionNotFoundException.class);
    }

    @Inject
    PersistenceExceptionLivenessCheck check;

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        try {
            return context.proceed();
        } catch (Exception ex) {
            if (!IGNORED.contains(ex.getClass())) { // suspect and rethrow unless ignored
                check.suspectSuper();
            }
            throw ex;
        }
    }

}
