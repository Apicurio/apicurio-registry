package io.apicurio.registry.metrics;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import io.apicurio.registry.rest.RegistryExceptionMapper;
import io.apicurio.registry.storage.AlreadyExistsException;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.InvalidArtifactStateException;
import io.apicurio.registry.storage.NotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StorageException;
import io.apicurio.registry.storage.VersionNotFoundException;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

/**
 * Fail liveness check if the number of exceptions thrown by storage is too high.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Interceptor
@PersistenceExceptionLivenessApply
public class PersistenceExceptionLivenessInterceptor {

    private static final Set<Class<? extends Throwable>> IGNORED = new HashSet<>();

    static {
        // Inherit from io.apicurio.registry.rest.RegistryExceptionMapper
        // to avoid manual sync, even though some exceptions may be irrelevant
        for (Map.Entry<Class<? extends Throwable>, Integer> entry : RegistryExceptionMapper.getCodeMap().entrySet()) {
            if(entry.getValue() != HTTP_INTERNAL_ERROR) // Sanity check
                IGNORED.add(entry.getKey());
        }
        // Additional ignored exceptions (may be already included)
        IGNORED.add(AlreadyExistsException.class);
        IGNORED.add(ArtifactAlreadyExistsException.class);
        IGNORED.add(ArtifactNotFoundException.class);
        IGNORED.add(InvalidArtifactStateException.class);
        IGNORED.add(NotFoundException.class);
        IGNORED.add(RegistryStorageException.class);
        IGNORED.add(RuleAlreadyExistsException.class);
        IGNORED.add(RuleNotFoundException.class);
        IGNORED.add(StorageException.class);
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
