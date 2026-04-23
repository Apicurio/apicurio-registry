package io.apicurio.registry.auth.opawasm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.styra.opa.wasm.OpaPolicy;
import com.styra.opa.wasm.OpaPolicyPool;

import io.quarkus.runtime.Startup;
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
        String dataPath = config.getDataPath();

        if (policyPath == null || policyPath.isBlank()) {
            log.warn("OPA WASM authorization is enabled but no policy path configured. "
                    + "Set apicurio.auth.opa-wasm.policy.path to a compiled .wasm file.");
            return;
        }

        log.info("Initializing OPA WASM authorization from policy: {}, data: {}", policyPath, dataPath);
        try {
            Path wasmPath = Path.of(policyPath);
            String permissionsData = "{}";
            if (dataPath != null && !dataPath.isBlank()) {
                permissionsData = Files.readString(Path.of(dataPath));
            }

            String finalPermissionsData = permissionsData;
            OpaPolicyPool pool = OpaPolicyPool.create(
                    () -> OpaPolicy.builder().withPolicy(wasmPath).build(),
                    config.getPoolSize());

            controller.initialize(pool, finalPermissionsData);
            log.info("OPA WASM authorization initialized with pool size {}.", config.getPoolSize());
        } catch (IOException e) {
            log.error("Failed to initialize OPA WASM authorization", e);
            throw new RuntimeException("Failed to load OPA WASM policy or data", e);
        }
    }
}
