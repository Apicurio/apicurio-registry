package io.apicurio.registry.auth.grants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes per-resource authorization denials to the dedicated {@code io.apicurio.registry.audit.authz}
 * log category, so operators can route or silence them independently of the regular application log.
 */
final class AuthzAuditLog {

    private static final Logger LOG = LoggerFactory.getLogger("io.apicurio.registry.audit.authz");

    private AuthzAuditLog() {
    }

    static void denied(String user, String operation, String resourceType, String resource) {
        LOG.info("authz.denied user=\"{}\" operation=\"{}\" resource_type=\"{}\" resource=\"{}\"", user,
                operation, resourceType, resource);
    }
}
