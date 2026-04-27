package io.apicurio.tests.auth;

import io.apicurio.registry.utils.tests.KeycloakTestContainerManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class OpaWasmTestResourceManager extends KeycloakTestContainerManager {

    private Path tempDir;

    @Override
    public Map<String, String> start() {
        Map<String, String> props = new java.util.HashMap<>(super.start());

        try {
            tempDir = Files.createTempDirectory("opa-wasm-test");

            Path wasmFile = tempDir.resolve("registry-authz.wasm");
            Path grantsFile = tempDir.resolve("opa-integration-test-grants.json");

            try (InputStream wasm = getClass().getClassLoader().getResourceAsStream("registry-authz.wasm")) {
                Files.copy(wasm, wasmFile, StandardCopyOption.REPLACE_EXISTING);
            }
            try (InputStream grants = getClass().getClassLoader()
                    .getResourceAsStream("opa-integration-test-grants.json")) {
                Files.copy(grants, grantsFile, StandardCopyOption.REPLACE_EXISTING);
            }

            props.put("apicurio.features.experimental.enabled", "true");
            props.put("apicurio.auth.opa-wasm.enabled", "true");
            props.put("apicurio.auth.opa-wasm.policy.path", wasmFile.toAbsolutePath().toString());
            props.put("apicurio.auth.opa-wasm.data.path", grantsFile.toAbsolutePath().toString());
            props.put("apicurio.auth.opa-wasm.pool-size", "2");

            props.put("apicurio.rest.deletion.group.enabled", "true");
            props.put("apicurio.rest.deletion.artifact.enabled", "true");
            props.put("apicurio.rest.deletion.artifact-version.enabled", "true");
        } catch (IOException e) {
            throw new RuntimeException("Failed to set up OPA WASM test resources", e);
        }

        return props;
    }

    @Override
    public synchronized void stop() {
        super.stop();
        if (tempDir != null) {
            try {
                Files.walk(tempDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            } catch (IOException ignored) {
            }
        }
    }
}
