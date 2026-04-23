package io.apicurio.registry.auth.opawasm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import com.styra.opa.wasm.OpaPolicy;
import com.styra.opa.wasm.OpaPolicyPool;

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

    private Path dataFilePath;
    private volatile FileTime lastModified;

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
                this.dataFilePath = Path.of(dataPath);
                permissionsData = Files.readString(dataFilePath);
                this.lastModified = Files.getLastModifiedTime(dataFilePath);
            }

            OpaPolicyPool pool = OpaPolicyPool.create(
                    () -> OpaPolicy.builder().withPolicy(wasmPath).build(),
                    config.getPoolSize());

            controller.initialize(pool, permissionsData);
            log.info("OPA WASM authorization initialized with pool size {}.", config.getPoolSize());
        } catch (IOException e) {
            log.error("Failed to initialize OPA WASM authorization", e);
            throw new RuntimeException("Failed to load OPA WASM policy or data", e);
        }
    }

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void checkForDataFileChanges() {
        if (dataFilePath == null || !config.isEnabled()) {
            return;
        }
        try {
            if (!Files.exists(dataFilePath)) {
                return;
            }
            FileTime currentModified = Files.getLastModifiedTime(dataFilePath);
            if (lastModified != null && currentModified.compareTo(lastModified) > 0) {
                log.info("Grants data file changed, reloading: {}", dataFilePath);
                String permissionsData = Files.readString(dataFilePath);
                controller.reloadData(permissionsData);
                lastModified = currentModified;
                log.info("Grants data reloaded successfully.");
            }
        } catch (IOException e) {
            log.error("Failed to check or reload grants data file: {}", dataFilePath, e);
        }
    }
}
