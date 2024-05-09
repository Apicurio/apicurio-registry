package io.apicurio.registry.storage.impl.sql;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.StorageMetricsApply;
import io.apicurio.registry.metrics.health.liveness.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.health.readiness.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.storage.RegistryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * An in-memory SQL implementation of the {@link RegistryStorage} interface.
 *
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@StorageMetricsApply
@Logged
public class SqlRegistryStorage extends AbstractSqlRegistryStorage {

    @Inject
    HandleFactory handleFactory;

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#storageName()
     */
    @Override
    public String storageName() {
        return "sql";
    }

    @Override
    public void initialize() {
        initialize(handleFactory, true);
    }

    public void restoreFromSnapshot(String snapshotLocation) {
        handleFactory.withHandleNoException(handle -> {
            handle.createUpdate(sqlStatements.restoreFromSnapshot())
                    .bind(0, snapshotLocation).execute();
        });
    }
}
