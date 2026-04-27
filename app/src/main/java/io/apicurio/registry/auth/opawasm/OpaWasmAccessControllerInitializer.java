package io.apicurio.registry.auth.opawasm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import io.apicurio.authz.OpaWasmAuthorizer;
import io.kroxylicious.authorizer.service.ResourceType;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

@Singleton
@Startup
public class OpaWasmAccessControllerInitializer {

    @Inject
    Logger log;

    @Inject
    OpaWasmAccessControllerConfig config;

    @Inject
    OpaWasmAccessController controller;

    @PostConstruct
    void init() {
        if (!config.isEnabled()) {
            log.debug("OPA WASM authorization is disabled.");
            return;
        }

        String policyPath = config.getPolicyPath();

        if (policyPath == null || policyPath.isBlank()) {
            log.warn("OPA WASM authorization is enabled but no policy path configured. "
                    + "Set apicurio.auth.opa-wasm.policy.path to a compiled .wasm file.");
            return;
        }

        String dataPath = config.getDataPath();
        log.info("Initializing OPA WASM authorization from policy: {}, data: {}", policyPath, dataPath);

        try {
            Map<Class<? extends ResourceType<?>>, String> resourceTypeNames = Map.of(
                    RegistryResourceType.Artifact.class, "artifact",
                    RegistryResourceType.Group.class, "group");

            OpaWasmAuthorizer authorizer = OpaWasmAuthorizer.create(
                    Path.of(policyPath),
                    dataPath != null && !dataPath.isBlank() ? Path.of(dataPath) : null,
                    config.getPoolSize(),
                    resourceTypeNames);

            controller.setAuthorizer(authorizer);
            log.info("OPA WASM authorization initialized with pool size {}.", config.getPoolSize());
        } catch (IOException e) {
            log.error("Failed to initialize OPA WASM authorization", e);
            throw new RuntimeException("Failed to load OPA WASM policy or data", e);
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
