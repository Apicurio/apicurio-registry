package io.apicurio.registry.auth.grants;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import io.apicurio.authz.AccessRulesAuthorizer;
import io.apicurio.authz.Artifact;
import io.apicurio.authz.Group;
import io.apicurio.registry.auth.AuthConfig;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

@Singleton
@Startup
public class GrantsAccessControllerInitializer {

    static final String RELOAD_JOB = "apicurio-access-rules-reload";

    @Inject
    Scheduler scheduler;

    @Inject
    Logger log;

    @Inject
    GrantsAccessControllerConfig config;

    @Inject
    GrantsAccessController controller;

    @Inject
    AuthConfig authConfig;

    /**
     * Loads the access rules file at startup. Misconfiguration fails startup rather than leaving the
     * registry running in a state where every request is denied.
     */
    @PostConstruct
    void init() {
        if (!config.isEnabled()) {
            log.debug("Per-resource authorization is disabled.");
            return;
        }
        if (!authConfig.isAuthenticationEnabled()) {
            log.warn("Per-resource authorization is enabled but authentication is disabled, so it has no "
                    + "effect. Enable OIDC, basic or proxy header authentication.");
        }

        String dataPath = config.getDataPath();
        if (dataPath == null || dataPath.isBlank()) {
            throw new IllegalStateException("Per-resource authorization is enabled but no access rules file "
                    + "is configured. Set apicurio.auth.resource-based-authorization.rules.path.");
        }

        logConfigurationWarnings();

        AccessRulesAuthorizer authorizer;
        try {
            // Rules for other resource types (e.g. Kroxylicious topics) are rejected: Registry
            // does not enforce them
            authorizer = AccessRulesAuthorizer.create(Path.of(dataPath), Set.of(Artifact.class, Group.class));
            controller.setAuthorizer(authorizer);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to load the per-resource authorization access rules file "
                    + dataPath + ": " + e.getMessage(), e);
        }
        if (config.isReloadEnabled()) {
            // Registered only when needed, so no timer runs when the feature or reload is disabled
            scheduler.newJob(RELOAD_JOB)
                    .setInterval(config.getReloadEvery())
                    .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                    .setTask(execution -> authorizer.checkForChanges())
                    .schedule();
        }
        log.info("Per-resource authorization initialized from {} (hot reload: {}).", dataPath,
                config.isReloadEnabled() ? "every " + config.getReloadEvery() : "disabled");
    }

    private void logConfigurationWarnings() {
        if (authConfig.isAnonymousReadsEnabled()) {
            log.info("Anonymous read access is enabled: anonymous callers can read only what the access rules "
                    + "allow to the role '{}'.", GrantsAccessController.ANONYMOUS_ROLE);
        }
        if (authConfig.isAuthenticatedReadsEnabled()) {
            log.info("Authenticated read access is enabled: authenticated callers skip the RBAC check for "
                    + "reads, but per-resource authorization still applies.");
        }
    }
}
