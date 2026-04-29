package io.apicurio.registry.auth.grants;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import io.apicurio.authz.GrantsAuthorizer;
import io.apicurio.authz.ResourceType;
import io.apicurio.registry.auth.AuthConfig;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

@Singleton
@Startup
public class GrantsAccessControllerInitializer {

    @Inject
    Logger log;

    @Inject
    GrantsAccessControllerConfig config;

    @Inject
    GrantsAccessController controller;

    @Inject
    AuthConfig authConfig;

    @PostConstruct
    void init() {
        if (!config.isEnabled()) {
            log.debug("Per-resource authorization is disabled.");
            return;
        }

        String dataPath = config.getDataPath();

        if (dataPath == null || dataPath.isBlank()) {
            log.warn("Per-resource authorization is enabled but no grants data path configured. "
                    + "Set apicurio.auth.resource-based-authorization.grants.path to a JSON grants file.");
            return;
        }

        logConfigurationWarnings();

        log.info("Initializing per-resource authorization from grants file: {}", dataPath);

        try {
            Map<Class<? extends ResourceType<?>>, String> resourceTypeNames = Map.of(
                    RegistryResourceType.Artifact.class, "artifact",
                    RegistryResourceType.Group.class, "group");

            GrantsAuthorizer authorizer = GrantsAuthorizer.create(
                    Path.of(dataPath), resourceTypeNames);

            controller.setAuthorizer(authorizer);
            log.info("Per-resource authorization initialized.");
        } catch (IOException e) {
            log.error("Failed to initialize per-resource authorization", e);
            throw new RuntimeException("Failed to load grants data", e);
        }
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

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void checkForDataFileChanges() {
        if (!config.isEnabled() || controller.getAuthorizer() == null) {
            return;
        }
        controller.getAuthorizer().checkForDataFileChanges();
    }
}
