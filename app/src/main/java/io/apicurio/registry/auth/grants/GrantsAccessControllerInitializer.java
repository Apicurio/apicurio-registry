package io.apicurio.registry.auth.grants;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import io.apicurio.authz.GrantsAuthorizer;
import io.apicurio.registry.auth.AuthConfig;
import io.kroxylicious.authorizer.service.ResourceType;
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

    static final String RELOAD_JOB = "apicurio-grants-reload";

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
     * Loads the grants file at startup. Misconfiguration fails startup rather than leaving the
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
            throw new IllegalStateException("Per-resource authorization is enabled but no grants file is "
                    + "configured. Set apicurio.auth.resource-based-authorization.grants.path.");
        }

        logConfigurationWarnings();

        Map<Class<? extends ResourceType<?>>, String> resourceTypeNames = Map.of(
                RegistryResourceType.Artifact.class, "artifact",
                RegistryResourceType.Group.class, "group");
        GrantsAuthorizer authorizer;
        try {
            authorizer = GrantsAuthorizer.create(Path.of(dataPath), resourceTypeNames);
            controller.setAuthorizer(authorizer);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Failed to load the per-resource authorization grants file "
                    + dataPath + ": " + e.getMessage(), e);
        }
        if (config.isReloadEnabled()) {
            // Registered only when needed, so no timer runs when the feature or reload is disabled
            scheduler.newJob(RELOAD_JOB)
                    .setInterval(config.getReloadEvery())
                    .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                    .setTask(execution -> authorizer.checkForDataFileChanges())
                    .schedule();
        }
        log.info("Per-resource authorization initialized from {} (hot reload: {}).", dataPath,
                config.isReloadEnabled() ? "every " + config.getReloadEvery() : "disabled");
    }

    private void logConfigurationWarnings() {
        if (authConfig.isAnonymousReadsEnabled()) {
            log.warn("Per-resource authorization is enabled alongside anonymous-read-access. "
                    + "Anonymous users will be able to read all resources regardless of grants.");
        }
        if (authConfig.isAuthenticatedReadsEnabled()) {
            log.warn("Per-resource authorization is enabled alongside authenticated-read-access. "
                    + "All authenticated users will be able to read all resources regardless of grants.");
        }
    }
}
