package io.apicurio.registry.storage.util;

import java.util.HashMap;
import java.util.Map;

/**
 * PostgreSQL profile for the contract outbox transaction tests. PostgreSQL is required because it is one
 * of the two dialects for which {@code SqlEventRepository.supportsDatabaseEvents()} is true, so it is the
 * real SQL outbox path rather than a no-op.
 * <p>
 * Those tests deliberately force storage failures, which the persistence liveness check would otherwise
 * count against its default threshold of 1, flipping the check to DOWN for the rest of the application
 * lifetime. The overrides below raise that threshold and silence the expected stack traces.
 * </p>
 */
public class ContractOutboxTestProfile extends PostgresqlTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>(super.getConfigOverrides());
        overrides.put("apicurio.metrics.persistence-exception-liveness-check.error-threshold", "1000");
        overrides.put("apicurio.metrics.persistence-exception-liveness-check.logging.disabled", "true");
        return overrides;
    }
}
